package com.prog2.catalog.synchronization;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.TechnicalIntegrationTestServer;
import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.application.usecases.*;
import com.prog2.catalog.integration.domain.enums.*;
import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;
import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.model.CatalogState;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.client.adapter.WebClientCatalogSnapshotAdapter;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;
import com.prog2.catalog.synchronization.infrastructure.integration.adapter.IntegrationTechnicalAccessAdapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TechnicalSnapshotAccessTests {
    private final Clock clock = Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC);

    private InMemoryIntegrationSessionAdapter readySession() {
        var sessions = new InMemoryIntegrationSessionAdapter();
        var configuration = new IntegrationConfiguration("test-group", ProvisioningStatus.PROVISIONED,
                "redis.test", 6379, "test-group", TechnicalIntegrationTestServer.REDIS_PASSWORD,
                "catedra:sync:*", "kafka.test:9092", "test-group", "catedra.catalog.test-group");
        sessions.replace(new IntegrationSession(TechnicalIntegrationTestServer.TOKEN,
                SnapshotFixtures.TIME.plusSeconds(60), IntegrationState.READY, configuration));
        return sessions;
    }

    private CatalogSnapshotRepository repository() {
        var repository = mock(CatalogSnapshotRepository.class);
        when(repository.finishAttempt(any(), any(), any())).thenReturn(new CatalogState(
                null, null, null, null, null, SynchronizationPhase.FAILED, null, "FICTIONAL_FAILURE"));
        return repository;
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void only401InvalidatesTheRealSessionAndNoFailureTriggersAnotherLogin(int status) throws Exception {
        try (var server = new TechnicalIntegrationTestServer()) {
            int requests = status == 401 ? 1 : 3;
            for (int i = 0; i < requests; i++) {
                server.respond(status, "{}");
            }
            var sessions = readySession();
            var tokens = new GetTechnicalTokenUseCaseImpl(sessions, clock);
            var access = new IntegrationTechnicalAccessAdapter(tokens,
                    new InvalidateTechnicalAccessUseCaseImpl(sessions),
                    new GetIntegrationConfigurationUseCaseImpl(sessions, clock));
            var json = JsonMapper.builder().build();
            var gateway = new WebClientCatalogSnapshotAdapter(server.baseUrl(), json,
                    new CatalogSnapshotResponseMapper(json), Duration.ofSeconds(1), Duration.ofSeconds(1),
                    Duration.ofSeconds(2), 8192);
            var cycle = new SynchronizeCatalogUseCaseImpl(gateway, repository(), access, clock, Duration.ZERO, new java.util.concurrent.locks.ReentrantLock());
            var result = cycle.synchronize();
            assertThat(result.phase()).isEqualTo(SynchronizationPhase.FAILED);
            assertThat(result.failureKind()).isEqualTo(status == 401
                    ? com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind.UNAUTHORIZED
                    : com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind.TEMPORARY);
            assertThat(server.requests()).hasSize(requests).allSatisfy(request ->
                    assertThat(request.path()).isEqualTo("/api/synchronization/snapshot"));
            if (status == 401) {
                assertThatThrownBy(tokens::getToken).isInstanceOf(IntegrationUnavailableException.class);
                assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
            } else {
                assertThat(tokens.getToken()).isEqualTo(TechnicalIntegrationTestServer.TOKEN);
            }
        }
    }

    @Test
    void expiryPreventsTheDownloadAndClosesAccessWithoutAuthenticating() {
        var sessions = readySession();
        Clock expiredClock = Clock.offset(clock, Duration.ofSeconds(60));
        var access = new IntegrationTechnicalAccessAdapter(new GetTechnicalTokenUseCaseImpl(sessions, expiredClock),
                new InvalidateTechnicalAccessUseCaseImpl(sessions),
                new GetIntegrationConfigurationUseCaseImpl(sessions, expiredClock));
        var gateway = mock(CatalogSnapshotGateway.class);
        var cycle = new SynchronizeCatalogUseCaseImpl(gateway, repository(), access, expiredClock, Duration.ZERO, new java.util.concurrent.locks.ReentrantLock());
        assertThat(cycle.synchronize().attempts()).isEqualTo(1);
        assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        verifyNoInteractions(gateway);
    }
}
