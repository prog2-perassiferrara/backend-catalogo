package com.prog2.catalog.security;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.prog2.catalog.infrastructure.security.config.SecurityConfiguration;
import com.prog2.catalog.integration.application.usecases.GetTechnicalTokenUseCaseImpl;
import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.enums.ProvisioningStatus;
import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;
import com.prog2.catalog.integration.infrastructure.web.controller.IntegrationTokenController;
import com.prog2.catalog.integration.infrastructure.web.dto.TechnicalTokenResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Prueba firma, filtros y adaptador HTTP reales; el spy solo observa la consulta de sesión. */
@WebMvcTest(controllers = IntegrationTokenController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfiguration.class, GetTechnicalTokenUseCaseImpl.class,
        InMemoryIntegrationSessionAdapter.class, SecurityHttpTests.Fixtures.class,
        SecurityHttpTests.UserProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@MockitoSpyBean(types = GetTechnicalTokenUseCase.class)
class SecurityHttpTests {

    private static final String DELIVERY = "/api/internal/catedra/token";
    private static final String CENTRAL_TOKEN = "fictional-central-secret";

    private final MockMvc mvc;

    private final InMemoryIntegrationSessionAdapter sessions;

    private final GetTechnicalTokenUseCase technicalTokens;

    /** El spy se declara sobre el bean; el constructor recibe su referencia ya preparada por Spring. */
    SecurityHttpTests(MockMvc mvc, InMemoryIntegrationSessionAdapter sessions,
            GetTechnicalTokenUseCase technicalTokens) {
        this.mvc = mvc;
        this.sessions = sessions;
        this.technicalTokens = technicalTokens;
    }

    @DynamicPropertySource
    static void securitySettings(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
        registry.add("CORS_ALLOWED_ORIGINS", () -> "https://app.test");
    }

    @BeforeEach
    void resetSession() {
        sessions.replace(ready(JwtTestTokens.NOW.plusSeconds(1000)));
        clearInvocations(technicalTokens);
    }

    @Test
    void rejectsAnonymousAndMalformedBearerBeforeReadingSession() throws Exception {
        problem(mvc.perform(get(DELIVERY)), 401, "AUTHENTICATION_REQUIRED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        problem(request("malformed-fictional-token"), 401, "AUTHENTICATION_REQUIRED");
        problem(mvc.perform(get(DELIVERY).header(HttpHeaders.AUTHORIZATION, "Bearer first second")),
                401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void rejectsOtherKeyUnsignedAndWrongAlgorithms() throws Exception {
        Map<String, Object> claims = JwtTestTokens.claims("service");
        problem(request(JwtTestTokens.signed(claims, JwtTestTokens.rsaKeyPair(2048), JWSAlgorithm.RS256)),
                401, "AUTHENTICATION_REQUIRED");
        problem(request(new PlainJWT(JWTClaimsSet.parse(claims)).serialize()), 401, "AUTHENTICATION_REQUIRED");
        problem(request(JwtTestTokens.signed(claims, JwtTestTokens.KEYS, JWSAlgorithm.RS512)),
                401, "AUTHENTICATION_REQUIRED");
        // Una pública conocida no debe poder reutilizarse como secreto HMAC para falsificar identidad.
        JWSObject hmac = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload(claims));
        hmac.sign(new MACSigner(JwtTestTokens.KEYS.getPublic().getEncoded()));
        problem(request(hmac.serialize()), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(technicalTokens);
    }

    @ParameterizedTest(name = "claim inválido: {0} = {1}")
    @MethodSource("invalidClaims")
    void rejectsSignedTokensOutsideContract(String claim, Object value) throws Exception {
        Map<String, Object> claims = JwtTestTokens.claims("service");
        if (value == null) {
            claims.remove(claim);
        } else {
            claims.put(claim, value);
        }
        problem(request(JwtTestTokens.signed(claims)), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(technicalTokens);
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(
                Arguments.of("iss", null), Arguments.of("iss", "other-service"),
                Arguments.of("aud", null), Arguments.of("aud", List.of("turnos")),
                Arguments.of("sub", null), Arguments.of("sub", " "),
                Arguments.of("token_use", null), Arguments.of("token_use", "technical"),
                Arguments.of("auth", null), Arguments.of("auth", List.of("ROLE_CATALOG_INTEGRATION")),
                Arguments.of("auth", 7),
                Arguments.of("auth", "ROLE_USER,ROLE_CATALOG_INTEGRATION"),
                Arguments.of("auth", "ROLE_USER  ROLE_CATALOG_INTEGRATION"),
                Arguments.of("auth", " ROLE_CATALOG_INTEGRATION"),
                Arguments.of("auth", "ROLE_CATALOG_INTEGRATION "),
                Arguments.of("iat", null), Arguments.of("iat", "not-an-instant"),
                Arguments.of("exp", null), Arguments.of("exp", "not-an-instant"),
                Arguments.of("nbf", "not-an-instant"),
                Arguments.of("iat", JwtTestTokens.NOW.plusSeconds(31).getEpochSecond()),
                Arguments.of("exp", JwtTestTokens.NOW.getEpochSecond()),
                Arguments.of("exp", JwtTestTokens.NOW.minusSeconds(1).getEpochSecond()),
                Arguments.of("exp", JwtTestTokens.NOW.plusSeconds(301).getEpochSecond()),
                Arguments.of("nbf", JwtTestTokens.NOW.plusSeconds(31).getEpochSecond()));
    }

    @Test
    void honorsClockSkewWithoutExtendingServiceLifetime() throws Exception {
        Map<String, Object> claims = JwtTestTokens.claims("service");
        claims.put("iat", JwtTestTokens.NOW.plusSeconds(30).getEpochSecond());
        claims.put("nbf", JwtTestTokens.NOW.plusSeconds(30).getEpochSecond());
        claims.put("exp", JwtTestTokens.NOW.plusSeconds(330).getEpochSecond());
        request(JwtTestTokens.signed(claims)).andExpect(status().isOk());

        claims.put("iat", JwtTestTokens.NOW.minusSeconds(330).getEpochSecond());
        claims.remove("nbf");
        claims.put("exp", JwtTestTokens.NOW.minusSeconds(30).getEpochSecond());
        request(JwtTestTokens.signed(claims)).andExpect(status().isOk());
        claims.put("exp", JwtTestTokens.NOW.minusSeconds(31).getEpochSecond());
        problem(request(JwtTestTokens.signed(claims)), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void onlyCompleteServiceIdentityCanReadCentralToken(CapturedOutput output) throws Exception {
        Map<String, Object> claims = JwtTestTokens.claims("service");
        request(JwtTestTokens.signed(claims)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"idToken\":\"" + CENTRAL_TOKEN + "\"}"))
                .andExpect(jsonPath("$.redisPassword").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(new TechnicalTokenResponse(CENTRAL_TOKEN).toString()).doesNotContain(CENTRAL_TOKEN);
        assertThat(output).doesNotContain(CENTRAL_TOKEN, "fictional-redis-password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "wrong-subject", "no-authority", "empty-authority"})
    void deniesValidTokensWithoutFullServicePermission(String scenario) throws Exception {
        Map<String, Object> claims = JwtTestTokens.claims("service");
        switch (scenario) {
            case "user" -> claims.put("token_use", "user");
            case "wrong-subject" -> claims.put("sub", "other-service");
            case "no-authority" -> claims.put("auth", "ROLE_USER");
            case "empty-authority" -> claims.put("auth", "");
            default -> throw new IllegalArgumentException(scenario);
        }
        problem(request(JwtTestTokens.signed(claims)), 403, "ACCESS_DENIED");
        sessions.replace(IntegrationSession.failed());
        problem(request(JwtTestTokens.signed(claims)), 403, "ACCESS_DENIED");
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void acceptsUsersWithTheirAuthorityButPreventsCrossingProfiles() throws Exception {
        Map<String, Object> user = JwtTestTokens.claims("user");
        user.put("aud", List.of("catalog", "turnos"));
        user.put("auth", "ROLE_USER ROLE_ADMIN");
        mvc.perform(get("/api/test-user").header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestTokens.signed(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("fictional-user-42"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        // La petición siguiente no hereda una sesión de la anterior.
        problem(mvc.perform(get("/api/test-user")), 401, "AUTHENTICATION_REQUIRED");
        user.put("auth", "ROLE_ADMIN");
        problem(mvc.perform(get("/api/test-user").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + JwtTestTokens.signed(user))), 403, "ACCESS_DENIED");
        var service = JwtTestTokens.claims("service");
        service.put("auth", "ROLE_USER ROLE_CATALOG_INTEGRATION");
        problem(mvc.perform(get("/api/test-user").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + JwtTestTokens.signed(service))), 403, "ACCESS_DENIED");
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void rejectsCentralJwtAndAnonymousRegardlessOfIntegrationState() throws Exception {
        sessions.replace(IntegrationSession.failed());
        problem(mvc.perform(get(DELIVERY)), 401, "AUTHENTICATION_REQUIRED");
        problem(request(JwtTestTokens.signed(JwtTestTokens.claims("service"),
                JwtTestTokens.rsaKeyPair(2048), JWSAlgorithm.RS256)), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void acceptsBearerPostWithoutCsrfTokenOrSession() throws Exception {
        String user = JwtTestTokens.signed(JwtTestTokens.claims("user"));
        var result = mvc.perform(post("/api/test-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("fictional-user-42"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void rejectsAnonymousPostWithoutCreatingSession() throws Exception {
        var result = problem(mvc.perform(post("/api/test-user")), 401, "AUTHENTICATION_REQUIRED")
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(technicalTokens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"initial", "failed", "pending", "missing-token", "expired"})
    void returnsUnavailableWithoutRenewalForUnusableCentralSession(String scenario, CapturedOutput output) throws Exception {
        IntegrationSession session = switch (scenario) {
            case "initial" -> IntegrationSession.initial();
            case "failed" -> IntegrationSession.failed();
            case "pending" -> new IntegrationSession(CENTRAL_TOKEN, null, IntegrationState.INITIALIZING, null);
            case "missing-token" -> new IntegrationSession(null, null, IntegrationState.READY, configuration());
            case "expired" -> ready(JwtTestTokens.NOW);
            default -> throw new IllegalArgumentException(scenario);
        };
        sessions.replace(session);
        problem(request(JwtTestTokens.signed(JwtTestTokens.claims("service"))),
                503, "TECHNICAL_INTEGRATION_UNAVAILABLE");
        assertThat(output).doesNotContain(CENTRAL_TOKEN, "fictional-redis-password");
        if ("expired".equals(scenario)) {
            assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        }
    }

    @Test
    void acceptsCentralTokenWithoutKnownExpiryAndDoesNotPreserveItAcrossInstances() throws Exception {
        sessions.replace(ready(null));
        request(JwtTestTokens.signed(JwtTestTokens.claims("service"))).andExpect(status().isOk());
        assertThat(new InMemoryIntegrationSessionAdapter().get()).isEqualTo(IntegrationSession.initial());
    }

    @Test
    void deniesUnpublishedInternalRoutesOtherMethodsAndExternalErrorCalls() throws Exception {
        String service = JwtTestTokens.signed(JwtTestTokens.claims("service"));
        for (String path : List.of("/api/internal/other", "/error", "/actuator/env", "/actuator")) {
            problem(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + service)),
                    403, "ACCESS_DENIED");
        }
        problem(mvc.perform(post(DELIVERY).header(HttpHeaders.AUTHORIZATION, "Bearer " + service)),
                403, "ACCESS_DENIED");
        verifyNoInteractions(technicalTokens);
    }

    @Test
    void handlesOnlyExplicitCorsOriginsWithoutGrantingAuthentication() throws Exception {
        mvc.perform(options(DELIVERY).header(HttpHeaders.ORIGIN, "https://app.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.test"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options(DELIVERY).header(HttpHeaders.ORIGIN, "https://untrusted.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        problem(mvc.perform(get(DELIVERY).header(HttpHeaders.ORIGIN, "https://app.test")),
                401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(technicalTokens);
    }

    private ResultActions request(String bearer) throws Exception {
        return mvc.perform(get(DELIVERY).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
    }

    private static ResultActions problem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    private static IntegrationSession ready(java.time.Instant expires) {
        return new IntegrationSession(CENTRAL_TOKEN, expires, IntegrationState.READY, configuration());
    }

    private static IntegrationConfiguration configuration() {
        return new IntegrationConfiguration("test-group", ProvisioningStatus.PROVISIONED,
                "redis.test", 6379, "test-user", "fictional-redis-password", "test:namespace",
                "kafka.test:9092", "test-consumer", "test-topic");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        Clock clock() {
            return Clock.fixed(JwtTestTokens.NOW, ZoneOffset.UTC);
        }
    }

    /** Endpoint solo de prueba para observar identidad sin adelantar APIs de negocio. */
    @RestController
    static class UserProbeController {
        @RequestMapping(path = "/api/test-user", method = {RequestMethod.GET, RequestMethod.POST})
        Map<String, String> user(Authentication identity) {
            return Map.of("subject", identity.getName());
        }
    }
}
