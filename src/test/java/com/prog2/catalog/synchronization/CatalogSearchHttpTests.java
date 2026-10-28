package com.prog2.catalog.synchronization;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import com.prog2.catalog.synchronization.domain.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato de CU-04 con MVC, JWT firmado, casos de uso y MySQL reales. */
@Testcontainers
@SpringBootTest(classes = CatalogQueryTestConfiguration.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("catalog-query-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class CatalogSearchHttpTests {
    private static final String SEARCH = "/api/catalog/professionals";
    private static final String CATEGORIES = "/api/catalog/categories";
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("search_http").withUsername("search_test").withPassword("fictional-search-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }
    private final MockMvc mvc;
    private final CatalogQueryTestData data;
    CatalogSearchHttpTests(MockMvc mvc, CatalogQueryTestData data) { this.mvc = mvc; this.data = data; }
    @BeforeEach void setup() { CatalogSearchFixtures.install(data); }

    @Test void jsonContainsThePageAndStateWithoutPrivateMetadata() throws Exception {
        var before = data.persisted();
        user(SEARCH).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6))
                .andExpect(jsonPath("$.content[0].id").value(103))
                .andExpect(jsonPath("$.content[0].enabled").value(false))
                .andExpect(jsonPath("$.content[0].category.name").value("Clínica"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.catalog.state").value("CURRENT"))
                .andExpect(jsonPath("$.catalog.copyAvailable").value(true))
                .andExpect(jsonPath("$.catalog.appliedVersion").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        user(CATEGORIES).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].enabled").value(false));
        assertThat(data.persisted()).isEqualTo(before);
    }

    @Test void optionalCombinedFiltersAndPaginationKeepTheAgreedMeaning() throws Exception {
        user(SEARCH, "categoryId", "1", "name", "  aNA gó  ", "enabled", "true", "availableOn", "2026-11-02")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(101));
        user(SEARCH, "name", "   ").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
        user(SEARCH, "enabled", "false").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        user(SEARCH, "size", "1", "page", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(101)).andExpect(jsonPath("$.totalPages").value(6));
        user(SEARCH, "size", "100").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        user(SEARCH, "page", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(6));
        user(SEARCH, "categoryId", "999").andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalPages").value(0));
        user(SEARCH, "name", "x".repeat(100)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(Arguments.of("categoryId", "0"), Arguments.of("categoryId", "-1"),
                Arguments.of("categoryId", "abc"), Arguments.of("categoryId", "9223372036854775808"),
                Arguments.of("enabled", "perhaps"), Arguments.of("availableOn", "2026-02-30"),
                Arguments.of("availableOn", "not-a-date"), Arguments.of("page", "-1"),
                Arguments.of("page", "2147483648"), Arguments.of("page", "2147483647"),
                Arguments.of("size", "0"), Arguments.of("size", "101"),
                Arguments.of("size", "abc"), Arguments.of("name", "x".repeat(101)));
    }

    @ParameterizedTest @MethodSource("invalidInputs")
    void invalidParametersAreSafeBadRequests(String parameter, String value) throws Exception {
        problem(user(SEARCH, parameter, value), 400, "INVALID_SEARCH_CRITERIA");
    }

    @Test void noCopyAndReadFailureCannotMasqueradeAsEmptyResults() throws Exception {
        data.guard.requireVerification();
        data.jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        for (String path : List.of(SEARCH, CATEGORIES)) {
            problem(user(path), 503, "CATALOG_COPY_UNAVAILABLE");
        }
        CatalogSearchFixtures.install(data);
        var before = data.persisted();
        data.jdbc.execute("RENAME TABLE catalog_sync_state TO fictional_unavailable_state");
        try {
            for (String path : List.of(SEARCH, CATEGORIES)) { problem(user(path), 503, "CATALOG_UNAVAILABLE"); }
        } finally { data.jdbc.execute("RENAME TABLE fictional_unavailable_state TO catalog_sync_state"); }
        assertThat(data.persisted()).isEqualTo(before);
    }

    @Test void emptyConfirmedCatalogIsQueryableAndPreviousCopiesRemainVisibleWithoutCentralAccess() throws Exception {
        data.guard.requireVerification();
        data.sessions.replace(IntegrationSession.failed());
        for (String path : List.of(SEARCH, CATEGORIES)) {
            user(path).andExpect(status().isOk()).andExpect(jsonPath("$.catalog.state").value("STALE"))
                    .andExpect(jsonPath("$.catalog.copyAvailable").value(true));
        }
        data.snapshots.replace(UUID.randomUUID(), new CatalogSnapshot(8, data.clock.instant(), List.of(), List.of(), List.of()));
        for (String path : List.of(SEARCH, CATEGORIES)) {
            user(path).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.catalog.copyAvailable").value(true));
        }
    }

    @Test void bothRoutesRequireUserIdentityAndPreserveInternalValidation() throws Exception {
        for (String path : List.of(SEARCH, CATEGORIES)) {
            problem(mvc.perform(get(path)), 401, "AUTHENTICATION_REQUIRED");
            problem(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")), 401, "AUTHENTICATION_REQUIRED");
            problem(request(path, JwtTestTokens.claims("service")), 403, "ACCESS_DENIED");
            var missingRole = JwtTestTokens.claims("user");
            missingRole.put("auth", "");
            problem(request(path, missingRole), 403, "ACCESS_DENIED");
            var expired = JwtTestTokens.claims("user");
            expired.put("exp", JwtTestTokens.NOW.minusSeconds(31).getEpochSecond());
            problem(request(path, expired), 401, "AUTHENTICATION_REQUIRED");
        }
        problem(request("/api/internal/catalog/professionals/0", JwtTestTokens.claims("service")), 400, "INVALID_PROFESSIONAL_ID");
    }

    private ResultActions user(String path, String... params) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestTokens.signed(JwtTestTokens.claims("user")));
        for (int i = 0; i < params.length; i += 2) { request.param(params[i], params[i + 1]); }
        return mvc.perform(request);
    }

    private ResultActions request(String path, Map<String, Object> claims) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestTokens.signed(claims)));
    }

    private static void problem(ResultActions action, int status, String code) throws Exception {
        String body = action.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("fictional-central-access", "fictional-search-password",
                "safeDiagnostic", "jdbc:", "org.hibernate", "?");
    }
}

