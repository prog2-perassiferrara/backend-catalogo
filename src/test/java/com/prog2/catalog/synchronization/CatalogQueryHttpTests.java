package com.prog2.catalog.synchronization;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtros JWT, MVC, aplicación y consultas SQL reales; no se simula el resultado del endpoint. */
@Testcontainers
@SpringBootTest(classes = CatalogQueryTestConfiguration.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("catalog-query-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class CatalogQueryHttpTests {
    private static final String PROFESSIONAL = "/api/internal/catalog/professionals/101";
    private static final String SERVICE_STATUS = "/api/internal/catalog/synchronization";
    private static final String USER_STATUS = "/api/catalog/synchronization";
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("query_http").withUsername("query_test").withPassword("fictional-query-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }
    private final MockMvc mvc;
    private final CatalogQueryTestData data;
    CatalogQueryHttpTests(MockMvc mvc, CatalogQueryTestData data) { this.mvc = mvc; this.data = data; }
    @BeforeEach void setup() { data.reset(); }

    @Test void deliversTheCompleteAgendaWithFreshnessFlagsAndStableLocalTimes() throws Exception {
        service(PROFESSIONAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.professional.id").value(101))
                .andExpect(jsonPath("$.professional.categoryId").value(1))
                .andExpect(jsonPath("$.professional.enabled").value(false))
                .andExpect(jsonPath("$.weeklySchedules.length()").value(3))
                .andExpect(jsonPath("$.weeklySchedules[0].id").value(201))
                .andExpect(jsonPath("$.weeklySchedules[1].id").value(202))
                .andExpect(jsonPath("$.weeklySchedules[2].id").value(203))
                .andExpect(jsonPath("$.weeklySchedules[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.weeklySchedules[1].enabled").value(false))
                .andExpect(jsonPath("$.catalog.state").value("CURRENT"))
                .andExpect(jsonPath("$.catalog.appliedVersion").value(7))
                .andExpect(jsonPath("$.catalog.observedVersion").value(7))
                .andExpect(jsonPath("$.catalog.lastCheckedAt").value(JwtTestTokens.NOW.toString()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        service("/api/internal/catalog/professionals/102").andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklySchedules").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidIdsProduceSafeBadRequests(String id) throws Exception {
        problem(service("/api/internal/catalog/professionals/" + id + "?private=fictional-secret"),
                400, "INVALID_PROFESSIONAL_ID");
    }

    @Test void absenceIsOnlyReportedOnAFreshCopy() throws Exception {
        problem(service("/api/internal/catalog/professionals/999"), 404, "PROFESSIONAL_NOT_FOUND");
        data.guard.requireVerification();
        problem(service("/api/internal/catalog/professionals/999"), 503, "CATALOG_NOT_CURRENT");
    }

    @Test void anEmptyConfirmedSnapshotRemainsACopyWithVerifiableAbsence() throws Exception {
        var empty = new com.prog2.catalog.synchronization.domain.model.CatalogSnapshot(8, data.clock.instant(),
                List.of(), List.of(), List.of());
        data.snapshots.replace(UUID.randomUUID(), empty);
        data.updates.checked(new com.prog2.catalog.synchronization.domain.model.CatalogPublication(8, 1, data.clock.instant()));
        service(SERVICE_STATUS).andExpect(status().isOk()).andExpect(jsonPath("$.copyAvailable").value(true));
        problem(service(PROFESSIONAL), 404, "PROFESSIONAL_NOT_FOUND");
    }

    @Test void separatesDetailedServiceStatusFromUserSummaryWithoutSecrets() throws Exception {
        var before = data.persisted();
        String detail = service(SERVICE_STATUS).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedVersion").value(7)).andReturn().getResponse().getContentAsString();
        user(USER_STATUS).andExpect(status().isOk())
                .andExpect(content().json("{\"state\":\"CURRENT\",\"copyAvailable\":true}"));
        assertThat(detail).doesNotContain("fictional-central-access", "fictional-password", "runId", "safeDiagnostic", "redis");
        service(PROFESSIONAL).andExpect(status().isOk());
        assertThat(data.persisted()).isEqualTo(before);
    }

    @Test void noCopyAndFunctionalFailureRemainReadableWithoutInventingCurrent() throws Exception {
        data.guard.requireVerification();
        data.jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL, observed_version = NULL, phase = 'NOT_STARTED' WHERE id = 1");
        user(USER_STATUS).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_COPY"))
                .andExpect(jsonPath("$.copyAvailable").value(false));
        data.updates.report(SynchronizationPhase.FAILED, "FICTIONAL_PRIVATE_DIAGNOSTIC");
        service(SERVICE_STATUS).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FAILED"));
        problem(service(PROFESSIONAL), 503, "CATALOG_NOT_CURRENT");
    }

    @Test void readFailuresReturnUnavailableAndDoNotLeakDependencyDetails() throws Exception {
        var before = data.persisted();
        data.jdbc.execute("RENAME TABLE catalog_sync_state TO fictional_unavailable_state");
        try {
            problem(service(PROFESSIONAL), 503, "CATALOG_UNAVAILABLE");
            problem(service(SERVICE_STATUS), 503, "CATALOG_UNAVAILABLE");
            problem(user(USER_STATUS), 503, "CATALOG_UNAVAILABLE");
        } finally { data.jdbc.execute("RENAME TABLE fictional_unavailable_state TO catalog_sync_state"); }
        assertThat(data.persisted()).isEqualTo(before);
    }

    @Test void unusableSessionAndOldVerificationDoNotEnableTurnos() throws Exception {
        data.sessions.replace(IntegrationSession.failed());
        problem(service(PROFESSIONAL), 503, "CATALOG_NOT_CURRENT");
        user(USER_STATUS).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STALE"));
        data.reset();
        data.jdbc.update("UPDATE catalog_sync_state SET last_checked_at = ? WHERE id = 1", JwtTestTokens.NOW.minusSeconds(61));
        problem(service(PROFESSIONAL), 503, "CATALOG_NOT_CURRENT");
    }

    @Test void validatesRealJwtAndDeniesCrossingIdentityProfiles() throws Exception {
        for (String path : List.of(PROFESSIONAL, SERVICE_STATUS, USER_STATUS)) {
            problem(mvc.perform(get(path)), 401, "AUTHENTICATION_REQUIRED");
            problem(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")), 401, "AUTHENTICATION_REQUIRED");
        }
        problem(user(PROFESSIONAL), 403, "ACCESS_DENIED");
        problem(user(SERVICE_STATUS), 403, "ACCESS_DENIED");
        problem(service(USER_STATUS), 403, "ACCESS_DENIED");
        var impostor = JwtTestTokens.claims("user");
        impostor.put("auth", "ROLE_USER ROLE_CATALOG_INTEGRATION");
        problem(request(PROFESSIONAL, impostor), 403, "ACCESS_DENIED");
        var wrongSubject = JwtTestTokens.claims("service");
        wrongSubject.put("sub", "other-service");
        problem(request(PROFESSIONAL, wrongSubject), 403, "ACCESS_DENIED");
        var missingRole = JwtTestTokens.claims("service");
        missingRole.put("auth", "ROLE_USER");
        problem(request(SERVICE_STATUS, missingRole), 403, "ACCESS_DENIED");
        var expired = JwtTestTokens.claims("service");
        expired.put("exp", JwtTestTokens.NOW.minusSeconds(31).getEpochSecond());
        problem(request(PROFESSIONAL, expired), 401, "AUTHENTICATION_REQUIRED");
        String wrongSignature = JwtTestTokens.signed(JwtTestTokens.claims("service"), JwtTestTokens.rsaKeyPair(2048),
                com.nimbusds.jose.JWSAlgorithm.RS256);
        problem(mvc.perform(get(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongSignature)), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test void preservesHealthTokenDeliveryAndDeniesUnpublishedRoutesAndMethods() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        service("/api/internal/catedra/token").andExpect(status().isOk())
                .andExpect(jsonPath("$.idToken").value("fictional-central-access"));
        problem(user("/api/internal/catedra/token"), 403, "ACCESS_DENIED");
        problem(service("/api/internal/catalog/other"), 403, "ACCESS_DENIED");
        problem(mvc.perform(post(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + JwtTestTokens.signed(JwtTestTokens.claims("service")))), 403, "ACCESS_DENIED");
    }

    private ResultActions service(String path) throws Exception { return request(path, JwtTestTokens.claims("service")); }
    private ResultActions user(String path) throws Exception { return request(path, JwtTestTokens.claims("user")); }
    private ResultActions request(String path, Map<String, Object> claims) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestTokens.signed(claims)));
    }
    private static void problem(ResultActions result, int status, String code) throws Exception {
        String body = result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("fictional-secret", "fictional_unavailable_state", "fictional-password", "SQLException", "idToken");
    }
}
