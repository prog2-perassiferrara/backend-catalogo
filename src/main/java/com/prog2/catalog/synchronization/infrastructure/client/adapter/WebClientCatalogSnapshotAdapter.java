package com.prog2.catalog.synchronization.infrastructure.client.adapter;

import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.CatalogSnapshot;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogSnapshotGateway;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;

/** Adapter síncrono de snapshot. HTTP, límites y JSON permanecen fuera del dominio. */
public class WebClientCatalogSnapshotAdapter implements CatalogSnapshotGateway {
    private static final String COMMUNICATION_FAILURE = "SNAPSHOT_COMMUNICATION_FAILURE";
    private final WebClient client;
    private final CatalogSnapshotResponseMapper snapshotMapper;
    private final JsonMapper jsonMapper;
    private final Duration requestTimeout;

    /** Configura tiempos finitos y un límite de respuesta, sin reintentos TCP implícitos. */
    public WebClientCatalogSnapshotAdapter(URI baseUrl, JsonMapper jsonMapper,
            CatalogSnapshotResponseMapper snapshotMapper, Duration connectTimeout,
            Duration readTimeout, Duration requestTimeout, int maxBytes) {
        this.snapshotMapper = snapshotMapper;
        this.jsonMapper = jsonMapper;
        this.requestTimeout = requestTimeout;
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(readTimeout).disableRetry(true);
        client = WebClient.builder().baseUrl(baseUrl.toString())
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes)).build();
    }

    @Override
    public CatalogSnapshot fetch(String token) {
        Payload payload;
        // El callback ocurre en otro hilo. Conservar el estado aunque el cuerpo exceda límites o falle.
        AtomicInteger receivedStatus = new AtomicInteger();
        try {
            payload = client.get().uri("/api/synchronization/snapshot")
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchangeToMono(response -> {
                        receivedStatus.set(response.statusCode().value());
                        return response.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> new Payload(response.statusCode().value(), body));
                    })
                    .onErrorMap(IOException.class,
                            _ -> failure(SnapshotFailureKind.TEMPORARY, COMMUNICATION_FAILURE))
                    .onErrorMap(ReadTimeoutException.class,
                            _ -> failure(SnapshotFailureKind.TEMPORARY, COMMUNICATION_FAILURE))
                    .block(requestTimeout);
        } catch (CatalogSnapshotException failure) {
            throw transportFailure(receivedStatus.get(), failure.kind(), failure.getMessage());
        } catch (DataBufferLimitException _) {
            throw transportFailure(receivedStatus.get(), SnapshotFailureKind.TERMINAL, "SNAPSHOT_TOO_LARGE");
        } catch (WebClientRequestException | IllegalStateException _) {
            throw transportFailure(receivedStatus.get(), SnapshotFailureKind.TEMPORARY, COMMUNICATION_FAILURE);
        } catch (RuntimeException _) {
            throw transportFailure(receivedStatus.get(), SnapshotFailureKind.TERMINAL, "INVALID_CATALOG_RESPONSE");
        }
        if (payload == null) {
            throw failure(SnapshotFailureKind.TERMINAL, "INVALID_CATALOG_RESPONSE");
        }
        if (payload.status() != 200) {
            throw httpFailure(payload);
        }
        return snapshotMapper.read(payload.body());
    }

    private CatalogSnapshotException httpFailure(Payload payload) {
        if (payload.status() == 401) {
            return failure(SnapshotFailureKind.UNAUTHORIZED, "TECHNICAL_ACCESS_REJECTED");
        }
        // El anexo permite cuerpos sin code. Se reconoce únicamente un código contractual conocido.
        String code = "";
        try {
            var node = jsonMapper.readTree(payload.body());
            if (node != null && node.path("code").isString()) {
                code = node.path("code").asString();
            }
        } catch (RuntimeException _) {
            // No interpretar detail ni registrar el cuerpo.
        }
        if ((payload.status() == 403 || payload.status() == 404)
                && "STUDENT_INTEGRATION_NOT_FOUND".equals(code)) {
            return failure(SnapshotFailureKind.TERMINAL, "INTEGRATION_NOT_FOUND");
        }
        SnapshotFailureKind kind = payload.status() == 500 || payload.status() == 503
                ? SnapshotFailureKind.TEMPORARY : SnapshotFailureKind.TERMINAL;
        return failure(kind, "SNAPSHOT_HTTP_" + payload.status());
    }

    private CatalogSnapshotException transportFailure(int status, SnapshotFailureKind kind, String diagnostic) {
        // Un 401 ya recibido invalida el acceso incluso si no se pudo leer su cuerpo.
        return status != 0 && status != 200 ? httpFailure(new Payload(status, "")) : failure(kind, diagnostic);
    }

    private static CatalogSnapshotException failure(SnapshotFailureKind kind, String diagnostic) {
        return new CatalogSnapshotException(kind, diagnostic);
    }

    private record Payload(int status, String body) {
        @Override
        public String toString() {
            return "Payload[status=" + status + "]";
        }
    }
}
