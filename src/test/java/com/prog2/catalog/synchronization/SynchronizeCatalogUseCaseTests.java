package com.prog2.catalog.synchronization;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class SynchronizeCatalogUseCaseTests {
    private final CatalogSnapshotGateway gateway = mock(CatalogSnapshotGateway.class);
    private final CatalogSnapshotRepository repository = mock(CatalogSnapshotRepository.class);
    private final TechnicalAccessGateway access = mock(TechnicalAccessGateway.class);
    private final Clock clock = Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC);
    private final CatalogSnapshot candidate = SnapshotFixtures.snapshot(7, 1);

    @BeforeEach
    void defaults() {
        when(access.getToken()).thenReturn("fictional-access");
        doReturn(candidate).when(gateway).fetch("fictional-access");
        when(repository.getState()).thenReturn(state(null, SynchronizationPhase.NOT_STARTED));
        when(repository.finishAttempt(any(), any(), anyString())).thenAnswer(invocation ->
                state(null, invocation.getArgument(1)));
    }

    private SynchronizeCatalogUseCaseImpl cycle() {
        return new SynchronizeCatalogUseCaseImpl(gateway, repository, access, clock, Duration.ZERO, new com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard());
    }

    private static CatalogState state(UUID runId, SynchronizationPhase phase) {
        return new CatalogState(runId == null ? null : 7L, runId, null, null, runId,
                phase, SnapshotFixtures.TIME, "FICTIONAL_STATE");
    }

    private static CatalogSnapshotException failure(SnapshotFailureKind kind) {
        return new CatalogSnapshotException(kind, "FICTIONAL_FAILURE");
    }

    @Test
    void retriesTemporaryDownloadThenAppliesWithoutAnotherLogin() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TEMPORARY))
                .thenReturn(candidate);
        SynchronizationResult result = cycle().synchronize();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(result.attempts()).isEqualTo(2);
        verify(gateway, times(2)).fetch("fictional-access");
        verify(repository).replace(any(UUID.class), same(candidate));
        verify(access, never()).invalidate();
    }

    @Test
    void retriesLocalTemporaryFailureWithTheSameCandidateAndRunId() {
        when(repository.replace(any(), any())).thenThrow(failure(SnapshotFailureKind.TEMPORARY))
                .thenReturn(state(UUID.randomUUID(), SynchronizationPhase.SNAPSHOT_APPLIED));
        assertThat(cycle().synchronize().attempts()).isEqualTo(2);
        verify(gateway).fetch("fictional-access");
        var ids = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(repository, times(2)).replace(ids.capture(), same(candidate));
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
    }

    @Test
    void stopsAtThreeTotalAttemptsWithoutPersistingAnUnvalidatedCopy() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TEMPORARY));
        SynchronizationResult result = cycle().synchronize();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.FAILED);
        assertThat(result.attempts()).isEqualTo(3);
        verify(gateway, times(3)).fetch("fictional-access");
        verify(repository, never()).replace(any(), any());
        verify(repository).finishAttempt(any(), eq(SynchronizationPhase.FAILED), eq("FICTIONAL_FAILURE"));
    }

    @Test
    void sharesTheBudgetAcrossDownloadAndStorageRatherThanThreeAttemptsEach() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TEMPORARY))
                .thenReturn(candidate);
        when(repository.replace(any(), any())).thenThrow(failure(SnapshotFailureKind.TEMPORARY));
        assertThat(cycle().synchronize().attempts()).isEqualTo(3);
        verify(gateway, times(2)).fetch("fictional-access");
        verify(repository, times(2)).replace(any(), same(candidate));
    }

    @Test
    void terminatesImmediatelyForInvalidSnapshotOrNonRecoverableStorageFailure() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TERMINAL));
        assertThat(cycle().synchronize().attempts()).isEqualTo(1);
        verify(repository, never()).replace(any(), any());
        doReturn(candidate).when(gateway).fetch("fictional-access");
        when(repository.replace(any(), any())).thenThrow(failure(SnapshotFailureKind.TERMINAL));
        assertThat(cycle().synchronize().attempts()).isEqualTo(1);
        verify(repository).replace(any(), same(candidate));
    }

    @Test
    void doesNotWriteAgainWhileAnUncertainCommitCannotBeVerified() {
        when(repository.replace(any(), any())).thenThrow(failure(SnapshotFailureKind.UNCERTAIN));
        when(repository.getState()).thenThrow(failure(SnapshotFailureKind.TEMPORARY));
        SynchronizationResult result = cycle().synchronize();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
        verify(repository).replace(any(), same(candidate));
        verify(gateway).fetch("fictional-access");
        verify(repository, times(3)).getState();
    }

    @Test
    void retriesTheSameCandidateAfterFreshMetadataShowsItWasNotApplied() {
        when(repository.replace(any(), any())).thenThrow(failure(SnapshotFailureKind.UNCERTAIN))
                .thenReturn(state(UUID.randomUUID(), SynchronizationPhase.SNAPSHOT_APPLIED));
        assertThat(cycle().synchronize().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        verify(repository).getState();
        verify(repository, times(2)).replace(any(), same(candidate));
        verify(gateway).fetch("fictional-access");
    }

    @Test
    void aConfirmedCommitStopsTheNextAttemptBeforeDownloadingOrWritingAgain() {
        when(repository.replace(any(), any())).thenAnswer(invocation -> {
            when(repository.getState()).thenReturn(state(invocation.getArgument(0),
                    SynchronizationPhase.SNAPSHOT_APPLIED));
            throw failure(SnapshotFailureKind.UNCERTAIN);
        });
        SynchronizationResult result = cycle().synchronize();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(result.attempts()).isEqualTo(2);
        verify(repository).getState();
        verify(repository).replace(any(), same(candidate));
        verify(gateway).fetch("fictional-access");
        verify(repository, never()).finishAttempt(any(), any(), any());
    }

    @Test
    void reconcilesAnUncertainCommitEvenWhenItOccursOnTheFinalAttempt() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TEMPORARY))
                .thenThrow(failure(SnapshotFailureKind.TEMPORARY)).thenReturn(candidate);
        when(repository.replace(any(), any())).thenAnswer(invocation -> {
            when(repository.getState()).thenReturn(state(invocation.getArgument(0),
                    SynchronizationPhase.SNAPSHOT_APPLIED));
            throw failure(SnapshotFailureKind.UNCERTAIN);
        });
        SynchronizationResult result = cycle().synchronize();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(result.attempts()).isEqualTo(3);
        verify(repository).replace(any(), same(candidate));
        verify(repository, never()).finishAttempt(any(), any(), any());
    }

    @Test
    void invalidatesRejectedAccessAndDoesNotRetry() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.UNAUTHORIZED));
        assertThat(cycle().synchronize().phase()).isEqualTo(SynchronizationPhase.FAILED);
        verify(access).invalidate();
        verify(gateway).fetch("fictional-access");
        verify(repository, never()).replace(any(), any());
    }

    @Test
    void reportsUnverifiableIfTheFailureDiagnosticCouldNotBeStored() {
        when(gateway.fetch("fictional-access")).thenThrow(failure(SnapshotFailureKind.TERMINAL));
        when(repository.finishAttempt(any(), any(), anyString())).thenThrow(failure(SnapshotFailureKind.TEMPORARY));
        assertThat(cycle().synchronize().phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
    }

    @Test
    void aTemporaryBeginFailureConsumesBudgetWithoutDownloadingTooEarly() {
        doThrow(failure(SnapshotFailureKind.TEMPORARY)).doNothing()
                .when(repository).beginAttempt(any(), any());
        assertThat(cycle().synchronize().attempts()).isEqualTo(2);
        verify(repository, times(2)).beginAttempt(any(), eq(SnapshotFixtures.TIME));
        verify(gateway).fetch("fictional-access");
    }

    @Test
    void aNewCycleDoesNotReuseThePreviousCandidateOrExecutionId() {
        var useCase = cycle();
        assertThat(useCase.synchronize().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(useCase.synchronize().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        var ids = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(repository, times(2)).replace(ids.capture(), same(candidate));
        assertThat(ids.getAllValues()).doesNotHaveDuplicates();
        verify(gateway, times(2)).fetch("fictional-access");
        verify(repository, times(2)).beginAttempt(any(), eq(SnapshotFixtures.TIME));
    }

    @Test
    void rejectsConcurrentCyclesBeforeTheyDownloadOrModifyMetadata() throws Exception {
        CountDownLatch downloading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.fetch("fictional-access")).thenAnswer(invocation -> {
            downloading.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return candidate;
        });
        var cycle = cycle();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(cycle::synchronize);
            try {
                assertThat(downloading.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(cycle.synchronize().phase()).isEqualTo(SynchronizationPhase.BUSY);
            } finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        } finally {
            release.countDown();
        }
        verify(gateway).fetch("fictional-access");
        verify(repository).beginAttempt(any(), any());
    }

    @Test
    void doesNotStartNewWorkOnAnAlreadyInterruptedThread() {
        Thread.currentThread().interrupt();
        try {
            assertThat(cycle().synchronize().phase()).isEqualTo(SynchronizationPhase.FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verifyNoInteractions(gateway, access);
        verify(repository, never()).beginAttempt(any(), any());
    }

    @Test
    void cancellationDuringDownloadDoesNotStartTheDatabaseReplacement() {
        when(gateway.fetch("fictional-access")).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return candidate;
        });
        try {
            assertThat(cycle().synchronize().phase()).isEqualTo(SynchronizationPhase.FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(repository, never()).replace(any(), any());
    }

    @Test
    void interruptionStopsTheRetryWaitWithoutStartingAnotherWrite() {
        when(repository.replace(any(), any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw failure(SnapshotFailureKind.TEMPORARY);
        });
        var useCase = new SynchronizeCatalogUseCaseImpl(gateway, repository, access, clock, Duration.ofSeconds(5), new com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard());
        try {
            SynchronizationResult result = useCase.synchronize();
            assertThat(result.phase()).isEqualTo(SynchronizationPhase.FAILED);
            assertThat(result.attempts()).isEqualTo(1);
            assertThat(result.safeDiagnostic()).isEqualTo("SNAPSHOT_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(repository).replace(any(), same(candidate));
        verify(gateway).fetch("fictional-access");
    }
}
