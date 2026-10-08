package com.prog2.catalog.integration.infrastructure.client.adapter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import io.netty.channel.ChannelOption;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.exception.CentralIntegrationException;
import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.infrastructure.client.dto.IntegrationResponse;
import com.prog2.catalog.integration.infrastructure.client.dto.LoginRequest;
import com.prog2.catalog.integration.infrastructure.client.dto.LoginResponse;
import com.prog2.catalog.integration.infrastructure.config.IntegrationProperties;

/**
 * Aplica el patrón Adapter al implementar {@link CentralIntegrationGateway} mediante WebClient.
 * Traduce operaciones de dominio a HTTP/JSON de cátedra y clasifica fallos sin divulgar respuestas.
 * Su uso es síncrono y se ejecuta desde el hilo dedicado a integración; el dominio recibe modelos.
 */
public class WebClientCentralIntegrationAdapter implements CentralIntegrationGateway {

    private final WebClient webClient;
    private final IntegrationProperties properties;
    private final JsonMapper mapper;
    private final Duration requestTimeout;

    /**
     * Configura esperas finitas y desactiva el reintento TCP implícito del cliente.
     *
     * @param properties URL e identidad técnica validadas
     * @param mapper lector de JSON para los DTO externos
     * @param timeout límite de conexión y respuesta; la espera global usa el doble de este valor
     */
    public WebClientCentralIntegrationAdapter(IntegrationProperties properties,
            JsonMapper mapper, Duration timeout) {
        this.properties = properties;
        this.mapper = mapper;
        this.requestTimeout = timeout.multipliedBy(2);
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout)
                // Reactor Netty reintenta una conexión abortada por defecto; el runner controla el límite.
                .disableRetry(true);
        this.webClient = WebClient.builder().baseUrl(properties.baseUrl().toString())
                .clientConnector(new ReactorClientHttpConnector(client)).build();
    }

    @Override
    public IntegrationSession authenticate() {
        HttpPayload payload = exchange(webClient.post().uri("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest(properties.username(), properties.password(), true)));
        try {
            LoginResponse response = mapper.readValue(payload.body(), LoginResponse.class);
            IntegrationConfiguration configuration = configuration(response.integration());
            Instant expiresAt = readExpiration(response.token());
            return new IntegrationSession(response.token(), expiresAt,
                    IntegrationState.INITIALIZING, configuration);
        } catch (CentralIntegrationException failure) {
            throw failure;
        } catch (RuntimeException ignored) {
            throw invalidResponse();
        }
    }

    @Override
    public IntegrationConfiguration getCurrentIntegration(String token) {
        HttpPayload payload = exchange(webClient.get().uri("/api/student/integration")
                .headers(headers -> headers.setBearerAuth(token)));
        try {
            return configuration(mapper.readValue(payload.body(), IntegrationResponse.class));
        } catch (CentralIntegrationException failure) {
            throw failure;
        } catch (RuntimeException ignored) {
            throw invalidResponse();
        }
    }

    private IntegrationConfiguration configuration(IntegrationResponse response) {
        if (response == null || !properties.groupId().equals(response.groupId())) {
            throw invalidResponse();
        }
        return response.toDomain();
    }

    /**
     * Ejecuta una llamada con espera finita. Los fallos de comunicación y HTTP 500/503
     * permiten reintentar; la integración ausente y los demás rechazos HTTP detienen el ciclo.
     */
    private HttpPayload exchange(WebClient.RequestHeadersSpec<?> request) {
        HttpPayload payload;
        try {
            payload = request.exchangeToMono(response -> response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new HttpPayload(response.statusCode().value(), body)))
                    .block(requestTimeout);
        } catch (WebClientRequestException | IllegalStateException ignored) {
            throw new CentralIntegrationException(true, "COMMUNICATION_FAILURE");
        } catch (RuntimeException ignored) {
            throw invalidResponse();
        }
        if (payload == null) {
            throw invalidResponse();
        }
        if (payload.status() != 200) {
            String code = errorCode(payload.body());
            if ("STUDENT_INTEGRATION_NOT_FOUND".equals(code)) {
                throw new CentralIntegrationException(false, "INTEGRATION_NOT_FOUND");
            }
            throw new CentralIntegrationException(payload.status() == 500 || payload.status() == 503,
                    "CENTRAL_HTTP_" + payload.status());
        }
        return payload;
    }

    private String errorCode(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            return node != null && node.has("code") ? node.get("code").asString("") : "";
        } catch (RuntimeException ignored) {
            // El anexo admite errores sin JSON o sin code. No interpretar ni registrar detail.
            return "";
        }
    }

    /**
     * Lee el vencimiento NumericDate del JWT sin verificar su firma ni asignar uno si falta.
     * La lectura admite fracciones de segundo; cátedra mantiene la autoridad sobre el token.
     */
    private Instant readExpiration(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw invalidResponse();
        }
        String[] parts = token.split("\\.");
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = mapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8));
        if (header == null || !header.isObject() || claims == null || !claims.isObject()) {
            throw invalidResponse();
        }
        if (!claims.has("exp")) {
            return null;
        }
        if (!claims.get("exp").isNumber()) {
            throw invalidResponse();
        }
        // Leer NumericDate no verifica firma. La autoridad sobre el token sigue siendo cátedra.
        BigDecimal seconds = claims.get("exp").decimalValue();
        return Instant.ofEpochSecond(seconds.toBigInteger().longValueExact(),
                seconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    private static CentralIntegrationException invalidResponse() {
        return new CentralIntegrationException(false, "INVALID_CENTRAL_RESPONSE");
    }

    private record HttpPayload(int status, String body) {
        @Override
        public String toString() {
            return "HttpPayload[status=" + status + "]";
        }
    }
}
