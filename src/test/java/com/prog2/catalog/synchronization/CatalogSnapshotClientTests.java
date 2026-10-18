package com.prog2.catalog.synchronization;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.TechnicalIntegrationTestServer;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.infrastructure.client.adapter.WebClientCatalogSnapshotAdapter;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;

import static org.assertj.core.api.Assertions.*;

@Timeout(15)
class CatalogSnapshotClientTests {
    private WebClientCatalogSnapshotAdapter client(TechnicalIntegrationTestServer server, Duration read,
            Duration global, int maxBytes) {
        var json = JsonMapper.builder().build();
        return new WebClientCatalogSnapshotAdapter(server.baseUrl(), json,
                new CatalogSnapshotResponseMapper(json), Duration.ofSeconds(1), read, global, maxBytes);
    }

    @Test
    void requestsTheSnapshotWithTheExistingBearerWithoutLoggingIn() throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            server.respond(200, SnapshotFixtures.json(7));
            assertThat(client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 8192)
                    .fetch(TechnicalIntegrationTestServer.TOKEN)).isEqualTo(SnapshotFixtures.snapshot(7, 1));
            assertThat(server.requests()).hasSize(1);
            var request = server.requests().getFirst();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/synchronization/snapshot");
            assertThat(request.authorization()).isEqualTo("Bearer " + TechnicalIntegrationTestServer.TOKEN);
        }
    }

    @ParameterizedTest
    @CsvSource({"400, TERMINAL", "401, UNAUTHORIZED", "403, TERMINAL", "404, TERMINAL",
            "500, TEMPORARY", "503, TEMPORARY"})
    void classifiesHttpEvenWithoutCodeAndDoesNotRetainSensitiveResponse(int status, SnapshotFailureKind expectedKind)
            throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            server.respond(status, "fictional-sensitive-body");
            var client = client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 8192);
            CatalogSnapshotException failure = catchThrowableOfType(
                    () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
            assertThat(failure.kind()).isEqualTo(expectedKind);
            assertThat(failure).hasNoCause().hasMessageNotContaining("fictional-sensitive-body");
            assertThat(server.requests()).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 503})
    void interpretsCodeTogetherWithStatusWithoutInterpretingDetail(int status) throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            server.respond(status, "{\"code\":\"STUDENT_INTEGRATION_NOT_FOUND\",\"detail\":\"ignored\"}");
            var client = client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 8192);
            assertThatThrownBy(() -> client.fetch(TechnicalIntegrationTestServer.TOKEN))
                    .hasMessage(status == 403 ? "INTEGRATION_NOT_FOUND" : "SNAPSHOT_HTTP_503");
        }
    }

    @Test
    void rejectsOversizedPayloadAsTerminal() throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            server.respond(200, SnapshotFixtures.json(7));
            var client = client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 128);
            CatalogSnapshotException failure = catchThrowableOfType(
                    () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
            assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.TERMINAL);
        }
    }

    @Test
    void boundsTheWholeRequestAndDoesNotHideTcpRetries() throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            CountDownLatch release = new CountDownLatch(1);
            server.waitBeforeResponding(release);
            var client = client(server, Duration.ofSeconds(2), Duration.ofMillis(150), 8192);
            try {
                CatalogSnapshotException failure = catchThrowableOfType(
                        () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
                assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.TEMPORARY);
                assertThat(server.requests()).hasSize(1);
            } finally {
                release.countDown();
            }
        }
        try (var server = new TechnicalIntegrationTestServer()) {
            server.disconnect();
            var client = client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 8192);
            CatalogSnapshotException failure = catchThrowableOfType(
                    () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
            assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.TEMPORARY);
            assertThat(server.requests()).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void keepsTheReceivedHttpStatusWhenTheErrorBodyExceedsTheLimit(int status) throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            server.respond(status, SnapshotFixtures.json(7));
            var client = client(server, Duration.ofSeconds(1), Duration.ofSeconds(2), 128);
            CatalogSnapshotException failure = catchThrowableOfType(
                    () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
            assertThat(failure.kind()).isEqualTo(status == 401
                    ? SnapshotFailureKind.UNAUTHORIZED : SnapshotFailureKind.TEMPORARY);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void interruptedOrStalledBodyAfterSuccessHeadersRemainsATemporaryDownloadFailure(boolean truncate)
            throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            if (!truncate) {
                try {
                    // El finally de la prueba libera el cuerpo después de comprobar el timeout del cliente.
                    release.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.close();
        });
        server.start();
        var json = JsonMapper.builder().build();
        var client = new WebClientCatalogSnapshotAdapter(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()), json,
                new CatalogSnapshotResponseMapper(json), Duration.ofSeconds(1), Duration.ofMillis(150),
                Duration.ofSeconds(2), 8192);
        try {
            CatalogSnapshotException failure = catchThrowableOfType(
                    () -> client.fetch(TechnicalIntegrationTestServer.TOKEN), CatalogSnapshotException.class);
            assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.TEMPORARY);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
