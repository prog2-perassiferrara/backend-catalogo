package com.prog2.catalog;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.prog2.catalog.integration.TechnicalIntegrationTestServer;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
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
            .withPassword(TEST_PASSWORD);

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
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GetTechnicalTokenUseCase technicalTokens;

    @Test
    void authenticatesAtStartupAndKeepsInternalTokenEndpointUnpublished() throws Exception {
        CENTRAL.awaitRequest();
        // Esperar una condición observable, sin adivinar cuánto demora el arranque asíncrono.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                .ignoreExceptionsInstanceOf(com.prog2.catalog.integration.application.exception.IntegrationUnavailableException.class)
                .untilAsserted(() ->
                assertThat(technicalTokens.getToken()).isEqualTo(TechnicalIntegrationTestServer.TOKEN));
        assertThat(CENTRAL.requests()).hasSize(1);
        assertThat(CENTRAL.requests().getFirst().path()).isEqualTo("/api/authenticate");
        assertThat(get("/api/internal/catedra/token").statusCode()).isEqualTo(404);
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
    void exposesHealthWithoutInternalDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator").statusCode()).isEqualTo(404);
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

    private HttpResponse<String> get(String path) throws Exception {
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
        return settings;
    }

    private static void assertStartupFails(Map<String, String> settings) {
        SpringApplication application = application(WebApplicationType.NONE);
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = application.run(arguments(settings))) {
                // Llegar a este punto significa que el arranque inválido fue aceptado.
            }
        }).isInstanceOf(RuntimeException.class);
    }

    private static SpringApplication application(WebApplicationType type) {
        SpringApplication application = new SpringApplication(CatalogApplication.class);
        application.setWebApplicationType(type);
        StandardEnvironment environment = new StandardEnvironment();
        // El resultado no debe depender de credenciales configuradas en la máquina del desarrollador.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        return application;
    }

    private static String[] arguments(Map<String, String> settings) {
        return settings.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
    }
}
