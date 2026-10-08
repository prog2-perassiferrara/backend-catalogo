package com.prog2.catalog.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.application.usecases.GetTechnicalTokenUseCaseImpl;
import com.prog2.catalog.integration.application.usecases.InitializeIntegrationUseCaseImpl;
import com.prog2.catalog.integration.domain.enums.AttemptOutcome;
import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;
import com.prog2.catalog.integration.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.integration.infrastructure.config.IntegrationStartupRunner;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;

import static com.prog2.catalog.integration.TechnicalIntegrationTestServer.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(15)
@ExtendWith(OutputCaptureExtension.class)
class IntegrationBehaviorTests {

    private TechnicalIntegrationTestServer server;
    private final InMemoryIntegrationSessionAdapter sessions = new InMemoryIntegrationSessionAdapter();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
    private InitializeIntegrationUseCaseImpl initialize;
    private GetTechnicalTokenUseCaseImpl tokens;

    @BeforeEach
    void setUp() throws Exception {
        server = new TechnicalIntegrationTestServer();
        initialize = initializer(sessions, Duration.ofSeconds(1));
        tokens = new GetTechnicalTokenUseCaseImpl(sessions, clock);
    }

    @AfterEach
    void closeServer() {
        server.close();
    }

    private InitializeIntegrationUseCaseImpl initializer(InMemoryIntegrationSessionAdapter repository,
            Duration timeout) {
        IntegrationProperties properties = new IntegrationProperties(server.baseUrl(),
                "test-user", PASSWORD, "test-group");
        return new InitializeIntegrationUseCaseImpl(new WebClientCentralIntegrationAdapter(properties,
                JsonMapper.builder().build(), timeout), repository, clock);
    }

    @Test
    void authenticatesWithLongLivedTokenAndPublishesCompleteSession(CapturedOutput output) throws Exception {
        server.respond(200, login(TOKEN, "PROVISIONED"));

        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.AVAILABLE);
        assertThat(tokens.getToken()).isEqualTo(TOKEN);
        var request = server.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/authenticate");
        var body = JsonMapper.builder().build().readTree(request.body());
        assertThat(body.get("username").asString()).isEqualTo("test-user");
        assertThat(body.get("password").asString()).isEqualTo(PASSWORD);
        assertThat(body.get("rememberMe").asBoolean()).isTrue();
        assertThat(sessions.get().toString()).doesNotContain(TOKEN, REDIS_PASSWORD);
        assertThat(sessions.get().configuration().toString()).doesNotContain(REDIS_PASSWORD);
        assertThat(output).doesNotContain(TOKEN, PASSWORD, REDIS_PASSWORD);
    }

    @Test
    void retainsSameTokenForPendingAndReadsUnwrappedConfiguration() {
        server.respond(200, login(TOKEN, "PENDING").replace(REDIS_PASSWORD, ""));
        server.respond(200, integration("PROVISIONED"));

        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.RETRYABLE);
        assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.AVAILABLE);
        assertThat(tokens.getToken()).isEqualTo(TOKEN);
        assertThat(server.requests()).hasSize(2);
        assertThat(server.requests().get(1).method()).isEqualTo("GET");
        assertThat(server.requests().get(1).path()).isEqualTo("/api/student/integration");
        assertThat(server.requests().get(1).authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "REVOKED", "UNKNOWN"})
    void stopsOnTerminalOrUnknownProvisioning(String status) {
        server.respond(200, login(TOKEN, status));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId", "redisHost", "redisPort", "redisUsername", "redisPassword",
            "redisReadNamespace", "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic"})
    void rejectsIncompleteProvisionedConfiguration(String field) throws Exception {
        var mapper = JsonMapper.builder().build();
        var json = (tools.jackson.databind.node.ObjectNode) mapper.readTree(login(TOKEN, "PROVISIONED"));
        ((tools.jackson.databind.node.ObjectNode) json.get("integration")).remove(field);
        server.respond(200, mapper.writeValueAsString(json));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void terminalHttpErrorsDoNotRetryOrExposeDetails(int status, CapturedOutput output) throws Exception {
        server.respond(status, "{\"detail\":\"" + PASSWORD + " " + TOKEN + "\"}");
        runCycle(Duration.ZERO);
        assertThat(server.requests()).hasSize(1);
        assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        assertThat(output).doesNotContain(PASSWORD, TOKEN);
    }

    @Test
    void recognizesIntegrationNotFoundCodeWithoutUsingDetail() {
        server.respond(404, "{\"code\":\"STUDENT_INTEGRATION_NOT_FOUND\",\"detail\":\"fictional\"}");
        var result = initialize.initialize();
        assertThat(result.outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(result.safeDiagnostic()).isEqualTo("INTEGRATION_NOT_FOUND");
    }

    @Test
    void rejectsMismatchedGroupAndWrappedGetResponse() {
        server.respond(200, login(TOKEN, "PROVISIONED").replace("test-group", "other-group"));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        sessions.replace(IntegrationSession.initial());
        server.respond(200, login(TOKEN, "PENDING"));
        server.respond(200, login(TOKEN, "PROVISIONED"));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.RETRYABLE);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json", "{\"id_token\":\"not-a-jwt\",\"integration\":{}}"})
    void rejectsMalformedResponses(String body, CapturedOutput output) {
        server.respond(200, body);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(output).doesNotContain(body);
    }

    @Test
    void stopsExactlyAtThreeAttemptsAndRequiresNewSessionForRecovery() throws Exception {
        server.respond(500, "{}");
        server.respond(503, "{}");
        server.respond(503, "{}");
        runCycle(Duration.ZERO);
        assertThat(server.requests()).hasSize(3);
        assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(server.requests()).hasSize(3);

        server.respond(200, login(TOKEN, "PROVISIONED"));
        var restartedSessions = new InMemoryIntegrationSessionAdapter();
        assertThat(initializer(restartedSessions, Duration.ofSeconds(1)).initialize().outcome())
                .isEqualTo(AttemptOutcome.AVAILABLE);
        assertThat(server.requests()).hasSize(4);
    }

    @Test
    void recoversWithinBoundedCycleAndDoesNotSubmitSecondCycle() throws Exception {
        server.respond(503, "{}");
        server.respond(200, login(TOKEN, "PENDING"));
        server.respond(200, integration("PROVISIONED"));
        runCycle(Duration.ZERO);
        assertThat(server.requests()).hasSize(3);
        assertThat(tokens.getToken()).isEqualTo(TOKEN);
        assertThat(server.requests().getLast().authorization()).isEqualTo("Bearer " + TOKEN);
    }

    private void runCycle(Duration delay) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var runner = new IntegrationStartupRunner(initialize, sessions, executor, delay)) {
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void cancelsWaitOnShutdownWithoutAnotherRequest(CapturedOutput output) throws Exception {
        server.respond(503, "{}");
        AtomicReference<Thread> worker = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task);
            worker.set(thread);
            return thread;
        });
        var runner = new IntegrationStartupRunner(initialize, sessions, executor, Duration.ofSeconds(5));
        try (runner) {
            runner.onApplicationReady();
            server.awaitRequest();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(() ->
                    output.getOut().contains("CENTRAL_HTTP_503")
                    && worker.get().getState() == Thread.State.TIMED_WAITING);
        }
        assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void timeoutIsFiniteAndRetryable() {
        CountDownLatch release = new CountDownLatch(1);
        server.waitBeforeResponding(release);
        try {
            var timed = initializer(sessions, Duration.ofMillis(150));
            assertThat(timed.initialize().outcome()).isEqualTo(AttemptOutcome.RETRYABLE);
            assertThat(server.requests()).hasSize(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void disconnectedResponseDoesNotTriggerHiddenClientRetry() {
        server.disconnect();
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.RETRYABLE);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void expiresExactlyAtExpWithoutNewLoginAndStaysFailedAfterClockMovesBack() {
        String token = token("{\"exp\":" + clock.instant().plusSeconds(60).getEpochSecond() + "}");
        server.respond(200, login(token, "PROVISIONED"));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.AVAILABLE);
        clock.advance(Duration.ofSeconds(59));
        assertThat(tokens.getToken()).isEqualTo(token);
        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
        clock.advance(Duration.ofSeconds(-60));
        assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(server.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"exp\":4102444800}"})
    void rejectsAcquiredTokenOn401RegardlessOfExpWithoutRenewing(String claims) {
        String token = token(claims);
        server.respond(200, login(token, "PENDING"));
        server.respond(401, "{}");
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.RETRYABLE);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void acceptsMissingExpAndRejectsAlreadyExpiredOrMalformedExp() {
        server.respond(200, login(token("{}"), "PROVISIONED"));
        assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.AVAILABLE);
        assertThat(tokens.getToken()).isEqualTo(token("{}"));
        for (String claims : new String[] {"{\"exp\":1}", "{\"exp\":\"invalid\"}", "{\"exp\":null}"}) {
            sessions.replace(IntegrationSession.initial());
            server.respond(200, login(token(claims), "PROVISIONED"));
            assertThat(initialize.initialize().outcome()).isEqualTo(AttemptOutcome.TERMINAL);
        }
    }

    private static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
