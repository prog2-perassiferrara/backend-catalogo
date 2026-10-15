package com.prog2.catalog.integration;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.infrastructure.config.IntegrationStartupRunner;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(15)
@ExtendWith(OutputCaptureExtension.class)
class IntegrationStartupRunnerTests {

    @Test
    void stopsAfterUnexpectedFailureWithoutExposingDetailsOrStartingAnotherCycle(CapturedOutput output)
            throws Exception {
        var initialize = mock(InitializeIntegrationUseCase.class);
        when(initialize.initialize()).thenThrow(new IllegalStateException("fictional-sensitive-detail"));
        var sessions = new InMemoryIntegrationSessionAdapter();
        var executor = Executors.newSingleThreadExecutor();

        try (var runner = new IntegrationStartupRunner(initialize, sessions, executor, Duration.ZERO)) {
            runner.onApplicationReady();
            // La tarea vacía espera detrás del ciclo en el mismo executor, sin pausas arbitrarias.
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            runner.onApplicationReady();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

            assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
            verify(initialize).initialize();
            assertThat(output).contains("Technical integration initialization failed; recovery requires restart")
                    .doesNotContain("fictional-sensitive-detail");
        }
    }
}
