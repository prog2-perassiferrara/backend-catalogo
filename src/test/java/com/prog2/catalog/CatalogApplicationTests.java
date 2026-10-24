package com.prog2.catalog;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.prog2.catalog.integration.TechnicalIntegrationTestServer;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogSnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@org.springframework.test.context.bean.override.mockito.MockitoBean(
        types = com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor.class)
class CatalogApplicationTests {

    private static final String TEST_PASSWORD = "fictional-test-password";
    private static final TechnicalIntegrationTestServer CENTRAL = centralServer();

    private static TechnicalIntegrationTestServer centralServer() {
        try {
            return new TechnicalIntegrationTestServer(TechnicalIntegrationTestServer.login(
                    TechnicalIntegrationTestServer.TOKEN, "PROVISIONED"));
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @AfterAll
    static void closeCentralServer() {
        CENTRAL.close();
    }

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("catalog_test")
            .withUsername("catalog_test")
            .withPassword(TEST_PASSWORD)
            // Base descartable: evita que un fsync lento del host agote el timeout JDBC de arranque.
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));

    @DynamicPropertySource
    static void databaseSettings(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("CATEDRA_BASE_URL", () -> CENTRAL.baseUrl().toString());
        registry.add("CATEDRA_USERNAME", () -> "test-user");
        registry.add("CATEDRA_PASSWORD", () -> TechnicalIntegrationTestServer.PASSWORD);
        registry.add("CATEDRA_GROUP_ID", () -> "test-group");
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }

    @LocalServerPort
    private int port;

    private final JdbcTemplate jdbc;

    private final GetTechnicalTokenUseCase technicalTokens;

    /** Spring entrega los beans del contexto real mediante el constructor de la prueba. */
    CatalogApplicationTests(JdbcTemplate jdbc, GetTechnicalTokenUseCase technicalTokens) {
        this.jdbc = jdbc;
        this.technicalTokens = technicalTokens;
    }

    @Test
    void authenticatesAtStartupAndProtectsInternalTokenEndpoint() throws Exception {
        CENTRAL.awaitRequest();
        // Esperar una condición observable, sin adivinar cuánto demora el arranque asíncrono.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                .ignoreExceptionsInstanceOf(com.prog2.catalog.integration.application.exception.IntegrationUnavailableException.class)
                .untilAsserted(() ->
                assertThat(technicalTokens.getToken()).isEqualTo(TechnicalIntegrationTestServer.TOKEN));
        // El primer snapshot también carga clientes/mappers; 5 s no es su plazo contractual.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id = 1",
                        Long.class)).isEqualTo(7L));
        assertThat(CENTRAL.requests()).hasSize(2);
        assertThat(CENTRAL.requests().getFirst().path()).isEqualTo("/api/authenticate");
        assertThat(CENTRAL.requests().get(1).path()).isEqualTo("/api/synchronization/snapshot");
        assertThat(get("/api/internal/catedra/token").statusCode()).isEqualTo(401);
        var claims = JwtTestTokens.claims("service");
        Instant issuedAt = Instant.now();
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", issuedAt.plusSeconds(300).getEpochSecond());
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/internal/catedra/token"))
                    .header("Authorization", "Bearer " + JwtTestTokens.signed(claims)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"idToken\":\"" + TechnicalIntegrationTestServer.TOKEN + "\"}");
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        }
        assertThat(CENTRAL.requests()).hasSize(2);
    }

    @Test
    void appliesFlywayMigrationAndPersistsItsData() {
        assertThat(jdbc.queryForObject(
                "SELECT description FROM setup_probe WHERE id = 1", String.class))
                .isEqualTo("fictional setup data");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    void eachRestartDownloadsANewSnapshotEvenWhenAConsistentCopyAlreadyExists() throws Exception {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id = 1",
                        Long.class)).isEqualTo(7L));
        UUID previous = UUID.fromString(jdbc.queryForObject(
                "SELECT applied_run_id FROM catalog_sync_state WHERE id = 1", String.class));
        try (var central = new TechnicalIntegrationTestServer(TechnicalIntegrationTestServer.login(
                TechnicalIntegrationTestServer.TOKEN, "PROVISIONED"))) {
            Map<String, String> settings = validSettings();
            settings.put("CATEDRA_BASE_URL", central.baseUrl().toString());
            settings.put("server.port", "0");
            for (int restart = 0; restart < 2; restart++) {
                UUID oldRunId = previous;
                try (var context = application(WebApplicationType.SERVLET).run(arguments(settings))) {
                    var repository = context.getBean(CatalogSnapshotRepository.class);
                    org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                        var state = repository.getState();
                        assertThat(state.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
                        assertThat(state.appliedRunId()).isNotEqualTo(oldRunId);
                    });
                    previous = repository.getState().appliedRunId();
                }
            }
            assertThat(central.requests()).extracting(TechnicalIntegrationTestServer.Request::path)
                    .containsExactly("/api/authenticate", "/api/synchronization/snapshot",
                            "/api/authenticate", "/api/synchronization/snapshot");
        }
    }

    @Test
    @Timeout(30)
    void rejectedSnapshotTokenClosesTechnicalAccessButPreservesThePreviousCopy() throws Exception {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id = 1",
                        Long.class)).isEqualTo(7L));
        try (var central = new TechnicalIntegrationTestServer()) {
            central.respond(200, TechnicalIntegrationTestServer.login(TechnicalIntegrationTestServer.TOKEN, "PROVISIONED"));
            central.respond(401, "{}");
            Map<String, String> settings = validSettings();
            settings.put("CATEDRA_BASE_URL", central.baseUrl().toString());
            settings.put("server.port", "0");
            try (var context = application(WebApplicationType.SERVLET).run(arguments(settings))) {
                var repository = context.getBean(CatalogSnapshotRepository.class);
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertThat(repository.getState().phase()).isEqualTo(SynchronizationPhase.FAILED));
                assertThat(repository.getState().appliedVersion()).isEqualTo(7L);
                GetTechnicalTokenUseCase tokens = context.getBean(GetTechnicalTokenUseCase.class);
                assertThatThrownBy(tokens::getToken)
                        .isInstanceOf(com.prog2.catalog.integration.application.exception.IntegrationUnavailableException.class);
                assertThat(context.isActive()).isTrue();
                assertThat(central.requests()).extracting(TechnicalIntegrationTestServer.Request::path)
                        .containsExactly("/api/authenticate", "/api/synchronization/snapshot");
            }
        }
    }

    @Test
    void exposesHealthWithoutInternalDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
        assertThat(get("/actuator").statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_NAME", "DB_USER", "DB_PASSWORD"})
    @Timeout(30)
    void rejectsBlankRequiredSettingWithoutLoggingPassword(String setting, CapturedOutput output) {
        Map<String, String> settings = validSettings();
        settings.put(setting, " ");

        assertStartupFails(settings);

        assertThat(output).contains("Required database setting is missing or blank: " + setting)
                .doesNotContain(TEST_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_NAME", "DB_USER", "DB_PASSWORD"})
    @Timeout(30)
    void rejectsMissingRequiredSettingWithoutLoggingPassword(String setting, CapturedOutput output) {
        Map<String, String> settings = validSettings();
        settings.remove(setting);

        assertStartupFails(settings);

        assertThat(output).contains(setting).doesNotContain(TEST_PASSWORD);
    }

    @Test
    @Timeout(30)
    void rejectsInvalidDatabasePortWithoutLoggingPassword(CapturedOutput output) {
        Map<String, String> settings = validSettings();
        settings.put("DB_PORT", "invalid-port");

        assertStartupFails(settings);

        assertThat(output).doesNotContain(TEST_PASSWORD);
    }

    @Test
    @Timeout(30)
    void rejectsMissingIntegrationSettingWithoutLoggingSecrets(CapturedOutput output) {
        Map<String, String> settings = validSettings();
        settings.remove("CATEDRA_PASSWORD");
        assertStartupFails(settings);
        assertThat(output).contains("CATEDRA_PASSWORD")
                .doesNotContain(TEST_PASSWORD, TechnicalIntegrationTestServer.PASSWORD);
    }

    @Test
    @Timeout(30)
    void rejectsMissingVerificationKeyWithoutLoggingSecrets(CapturedOutput output) {
        Map<String, String> settings = validSettings();
        settings.remove("JWT_PUBLIC_KEY_PATH");
        assertStartupFails(settings);
        assertThat(output).contains("JWT_PUBLIC_KEY_PATH")
                .doesNotContain(TEST_PASSWORD, TechnicalIntegrationTestServer.PASSWORD);
    }

    @Test
    @Timeout(30)
    void keepsHttpAndDatabaseAvailableWhenCentralIsUnavailable(CapturedOutput output) throws Exception {
        try (var unavailable = new TechnicalIntegrationTestServer()) {
            Map<String, String> settings = validSettings();
            settings.put("CATEDRA_BASE_URL", unavailable.baseUrl().toString());
            settings.put("server.port", "0");
            try (ConfigurableApplicationContext context = application(
                    WebApplicationType.SERVLET).run(arguments(settings))) {
                unavailable.awaitRequest();
                assertThat(context.isActive()).isTrue();
                assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT 1", Integer.class))
                        .isEqualTo(1);
                int httpPort = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
                try (HttpClient client = HttpClient.newHttpClient()) {
                    var response = client.send(HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + httpPort + "/actuator/health")).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
                }
                assertThatThrownBy(context.getBean(GetTechnicalTokenUseCase.class)::getToken)
                        .isInstanceOf(com.prog2.catalog.integration.application.exception.IntegrationUnavailableException.class);
            }
        }
        assertThat(output).doesNotContain(TEST_PASSWORD, TechnicalIntegrationTestServer.PASSWORD,
                TechnicalIntegrationTestServer.TOKEN);
    }

    @Test
    @Timeout(30)
    void failsStartupWhenDatabaseIsUnreachableWithoutLoggingPassword(CapturedOutput output) throws Exception {
        Map<String, String> settings = validSettings();
        // Reservar un puerto y cerrarlo: la conexión debe ser rechazada, no simular una base funcional.
        try (ServerSocket socket = new ServerSocket(0)) {
            settings.put("DB_PORT", Integer.toString(socket.getLocalPort()));
        }

        assertStartupFails(settings);

        assertThat(output).contains("Communications link failure").doesNotContain(TEST_PASSWORD);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static Map<String, String> validSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("DB_HOST", MYSQL.getHost());
        settings.put("DB_PORT", Integer.toString(MYSQL.getFirstMappedPort()));
        settings.put("DB_NAME", MYSQL.getDatabaseName());
        settings.put("DB_USER", MYSQL.getUsername());
        settings.put("DB_PASSWORD", MYSQL.getPassword());
        settings.put("CATEDRA_BASE_URL", CENTRAL.baseUrl().toString());
        settings.put("CATEDRA_USERNAME", "test-user");
        settings.put("CATEDRA_PASSWORD", TechnicalIntegrationTestServer.PASSWORD);
        settings.put("CATEDRA_GROUP_ID", "test-group");
        settings.put("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH.toString());
        return settings;
    }

    private static void assertStartupFails(Map<String, String> settings) {
        SpringApplication application = application(WebApplicationType.NONE);
        String[] startupArguments = arguments(settings);
        ConfigurableApplicationContext[] startedContext = new ConfigurableApplicationContext[1];
        try {
            assertThatThrownBy(() -> startedContext[0] = application.run(startupArguments))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            // Si arranca inesperadamente, cerrar el contexto sin confundir un error de cierre con uno de arranque.
            if (startedContext[0] != null) {
                startedContext[0].close();
            }
        }
    }

    private static SpringApplication application(WebApplicationType type) {
        SpringApplication application = new SpringApplication(CatalogApplication.class);
        application.setWebApplicationType(type);
        StandardEnvironment environment = new StandardEnvironment();
        // El resultado no debe depender de credenciales configuradas en la máquina del desarrollador.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        // Estas regresiones verifican CU-01 con REST ficticio. Kafka/Redis se prueban por separado.
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory ->
                factory.registerSingleton("catalogSynchronizationMonitor", org.mockito.Mockito.mock(
                        com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor.class))));
        return application;
    }

    private static String[] arguments(Map<String, String> settings) {
        return settings.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
    }
}
