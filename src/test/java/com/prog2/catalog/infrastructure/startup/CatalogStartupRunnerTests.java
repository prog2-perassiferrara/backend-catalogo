package com.prog2.catalog.infrastructure.startup;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.enums.AttemptOutcome;
import com.prog2.catalog.integration.domain.model.InitializationResult;
import com.prog2.catalog.integration.domain.ports.in.InvalidateTechnicalAccessUseCase;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.model.SynchronizationResult;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.application.usecases.InvalidateTechnicalAccessUseCaseImpl;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Timeout(15)
@ExtendWith(OutputCaptureExtension.class)
class CatalogStartupRunnerTests {

    @Test
    void stopsAfterUnexpectedFailureWithoutExposingDetailsOrStartingAnotherCycle(CapturedOutput output)
            throws Exception {
        var initialize = mock(InitializeIntegrationUseCase.class);
        when(initialize.initialize()).thenThrow(new IllegalStateException("fictional-sensitive-detail"));
        var sessions = new InMemoryIntegrationSessionAdapter();
        var executor = Executors.newSingleThreadExecutor();

        var synchronization = mock(SynchronizeCatalogUseCase.class);
        try (var runner = new CatalogStartupRunner(initialize, new InvalidateTechnicalAccessUseCaseImpl(sessions),
                synchronization, executor, Duration.ZERO)) {
            runner.onApplicationReady();
            // La tarea vacía espera detrás del ciclo en el mismo executor, sin pausas arbitrarias.
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

            assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
            verify(initialize).initialize();
            verifyNoInteractions(synchronization);
            assertThat(output).contains("Technical integration initialization failed; recovery requires restart")
                    .doesNotContain("fictional-sensitive-detail");
        }
    }

    @Test
    void waitsForTechnicalAccessAndStartsSnapshotOnlyOnce() throws Exception {
        var initialize = mock(InitializeIntegrationUseCase.class);
        var invalidation = mock(InvalidateTechnicalAccessUseCase.class);
        var synchronization = mock(SynchronizeCatalogUseCase.class);
        when(initialize.initialize()).thenReturn(new InitializationResult(AttemptOutcome.RETRYABLE, "PENDING"),
                new InitializationResult(AttemptOutcome.AVAILABLE, "READY"));
        when(synchronization.synchronize()).thenReturn(
                new SynchronizationResult(SynchronizationPhase.SNAPSHOT_APPLIED, 1, "SNAPSHOT_APPLIED"));
        var executor = Executors.newSingleThreadExecutor();
        try (var runner = new CatalogStartupRunner(initialize, invalidation, synchronization, executor, Duration.ZERO)) {
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            var order = inOrder(initialize, synchronization);
            order.verify(initialize, times(2)).initialize();
            order.verify(synchronization).synchronize();
            verifyNoInteractions(invalidation);
        }
    }

    @Test
    void snapshotFailureDoesNotInvalidateTheTechnicalSession(CapturedOutput output) throws Exception {
        var initialize = mock(InitializeIntegrationUseCase.class);
        var invalidation = mock(InvalidateTechnicalAccessUseCase.class);
        var synchronization = mock(SynchronizeCatalogUseCase.class);
        when(initialize.initialize()).thenReturn(new InitializationResult(AttemptOutcome.AVAILABLE, "READY"));
        when(synchronization.synchronize()).thenThrow(new IllegalStateException("fictional-sensitive-detail"));
        var executor = Executors.newSingleThreadExecutor();
        try (var runner = new CatalogStartupRunner(initialize, invalidation, synchronization, executor, Duration.ZERO)) {
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            verifyNoInteractions(invalidation);
            assertThat(output).contains("Catalog snapshot failed; recovery requires restart")
                    .doesNotContain("fictional-sensitive-detail");
        }
    }

    @Test
    void unavailableTechnicalAccessNeverStartsSnapshot() throws Exception {
        var initialize = mock(InitializeIntegrationUseCase.class);
        var invalidation = mock(InvalidateTechnicalAccessUseCase.class);
        var synchronization = mock(SynchronizeCatalogUseCase.class);
        when(initialize.initialize()).thenReturn(new InitializationResult(AttemptOutcome.RETRYABLE, "TEMPORARY"));
        var executor = Executors.newSingleThreadExecutor();
        try (var runner = new CatalogStartupRunner(initialize, invalidation, synchronization, executor, Duration.ZERO)) {
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            verify(initialize, times(3)).initialize();
            verify(invalidation).invalidate();
            verifyNoInteractions(synchronization);
        }
    }
}
