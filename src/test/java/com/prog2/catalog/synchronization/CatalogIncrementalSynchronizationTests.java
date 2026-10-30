package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import org.junit.jupiter.api.*;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class CatalogIncrementalSynchronizationTests {
    private final CatalogChangesGateway changes=mock(CatalogChangesGateway.class);
    private final CatalogUpdateRepository repository=mock(CatalogUpdateRepository.class);
    private final CatalogSnapshotRepository snapshots=mock(CatalogSnapshotRepository.class);
    private final RecoverCatalogUseCase recovery=mock(RecoverCatalogUseCase.class);
    private final TechnicalAccessGateway access=mock(TechnicalAccessGateway.class);
    private final Clock clock=mock(Clock.class);
    private final CatalogSynchronizationGuard lock=new CatalogSynchronizationGuard();
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        when(repository.getState()).thenReturn(state(7,null));
        when(recovery.resume()).thenReturn(Optional.empty());
    }
    private UpdateCatalogUseCaseImpl useCase() {
        return new UpdateCatalogUseCaseImpl(changes, repository, recovery, access, clock, 3, Duration.ZERO, Duration.ofSeconds(30), lock);
    }
    private static CatalogState state(long version,UUID run) {
        return new CatalogState(version,run,SnapshotFixtures.TIME,SnapshotFixtures.TIME,run,
                SynchronizationPhase.SNAPSHOT_APPLIED,SnapshotFixtures.TIME,"FICTIONAL_STATE");
    }

    @Test void checksWithoutKafkaAndAppliesOneCompleteRange() {
        var batch=CatalogUpdatePersistenceTests.batch(7,10,2);
        when(changes.readSince(7)).thenReturn(batch);
        when(repository.apply(any(),same(batch),isNull())).thenReturn(state(10,UUID.randomUUID()));
        assertThat(useCase().check().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        verify(repository).apply(any(),same(batch),isNull());
        verify(repository,never()).recordCovered(any());
    }

    @Test void temporaryFailureHasThreeAttemptsAndANextOpportunityThatMessagesCannotAdvance() {
        when(changes.readSince(7)).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY,"FICTIONAL_NETWORK"));
        var useCase=useCase();
        assertThat(useCase.check().retryAt()).isEqualTo(SnapshotFixtures.TIME.plusSeconds(30));
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(29));
        useCase.process(CatalogUpdatePersistenceTests.notice("later-message",10));
        verify(changes,times(3)).readSince(7);
        verify(recovery,never()).recover(any());
        verify(repository,never()).apply(any(),any(),any());
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(30));
        useCase.check();
        verify(changes,times(6)).readSince(7);
    }

    @Test void refusesToRepeatAnUncertainWriteWhenMetadataCannotBeRead() {
        when(changes.readSince(7)).thenReturn(CatalogUpdatePersistenceTests.batch(7,10,2));
        when(repository.apply(any(),any(),any())).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.UNCERTAIN,"FICTIONAL_COMMIT"));
        when(repository.getState()).thenReturn(state(7,null),state(7,null))
                .thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY,"FICTIONAL_READ"));
        var result=useCase().process(CatalogUpdatePersistenceTests.notice("uncertain",10));
        assertThat(result.recorded()).isFalse();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
        verify(repository).apply(any(),any(),any());
        verify(changes).readSince(7);
    }

    @Test void recognizesAnOldOrDuplicateNoticeWithoutReadingRedisOrInventingVigency() {
        var useCase=useCase();
        when(repository.isProcessed("duplicate")).thenReturn(true);
        assertThat(useCase.process(CatalogUpdatePersistenceTests.notice("duplicate",8)).recorded()).isTrue();
        var covered=useCase.process(CatalogUpdatePersistenceTests.notice("covered",6));
        assertThat(covered.recorded()).isTrue();
        assertThat(covered.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        verify(repository).recordCovered(CatalogUpdatePersistenceTests.notice("covered",6));
        verifyNoInteractions(changes,recovery);
    }

    @Test void permanentFailureStopsLaterChecksAndRejectedMessagesUntilRestart() {
        when(changes.readSince(7)).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TERMINAL,"FICTIONAL_PERMANENT"));
        var useCase=useCase();
        assertThat(useCase.check().failureKind()).isEqualTo(SnapshotFailureKind.TERMINAL);
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(3600));
        useCase.check();
        var result=useCase.reject(new RejectedCatalogRecord("fictional-topic",0,2,"INCOMPATIBLE_CATALOG_NOTIFICATION"));
        assertThat(result.terminal()).isTrue();
        assertThat(result.recorded()).isFalse();
        verify(changes).readSince(7);
        verify(repository,never()).recordRejection(any());
    }

    @Test void recoveryWaitBlocksUnsafeIncrementsWithoutResettingItsCycle() {
        var load=mock(SynchronizeCatalogUseCase.class);
        when(load.synchronize()).thenReturn(new SynchronizationResult(SynchronizationPhase.FAILED,3,
                "FICTIONAL_TEMPORARY",SnapshotFailureKind.TEMPORARY));
        var actualRecovery=new RecoverCatalogUseCaseImpl(load, repository, access, clock, 3, Duration.ofSeconds(30), Duration.ofMinutes(5), lock);
        var useCase=new UpdateCatalogUseCaseImpl(changes, repository, actualRecovery, access, clock, 3, Duration.ZERO, Duration.ofSeconds(30), lock);
        when(changes.readSince(7)).thenThrow(new CatalogDiscontinuityException(CatalogRecoveryReason.CHANGE_MISSING));
        assertThat(useCase.process(CatalogUpdatePersistenceTests.notice("missing",10)).phase()).isEqualTo(SynchronizationPhase.RECOVERY_WAITING);
        useCase.process(CatalogUpdatePersistenceTests.notice("another-message",11));
        verify(load).synchronize();
        verify(changes).readSince(7);
        verify(repository,never()).apply(any(),any(),any());
    }

    @Test void sharesExclusionWithAnActualSnapshotCycle() throws Exception {
        var gateway=mock(CatalogSnapshotGateway.class);
        var started=new CountDownLatch(1);
        var finish=new CountDownLatch(1);
        when(gateway.fetch(any())).thenAnswer(call -> {
            started.countDown();
            assertThat(finish.await(5,TimeUnit.SECONDS)).isTrue();
            return SnapshotFixtures.snapshot(7,1);
        });
        when(access.getToken()).thenReturn("fictional-access");
        when(snapshots.getState()).thenReturn(state(7,null));
        var snapshot=new SynchronizeCatalogUseCaseImpl(gateway, snapshots, access, clock, 3, Duration.ZERO, lock);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var running=executor.submit(snapshot::synchronize);
            try {
                assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(useCase().check().safeDiagnostic()).isEqualTo("CATALOG_BUSY");
                verifyNoInteractions(changes);
            } finally { finish.countDown(); }
            assertThat(running.get(5,TimeUnit.SECONDS).phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        }
    }

    @Test void retriesALocalTemporaryFailureWithTheSameValidatedCandidateAndRunId() {
        var batch = CatalogUpdatePersistenceTests.batch(7, 10, 2);
        when(changes.readSince(7)).thenReturn(batch);
        when(repository.apply(any(), same(batch), any()))
                .thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "FICTIONAL_LOCAL_FAILURE"))
                .thenReturn(state(10, UUID.randomUUID()));
        var result = useCase().process(CatalogUpdatePersistenceTests.notice("retried-write", 10));
        assertThat(result.recorded()).isTrue();
        var ids = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(repository, times(2)).apply(ids.capture(), same(batch), any());
        assertThat(ids.getAllValues().getFirst()).isEqualTo(ids.getAllValues().getLast());
        verify(changes).readSince(7);
    }

    @Test void doesNotAcknowledgeANotificationUntilRedisActuallyCoversItsVersion() {
        var partial = CatalogUpdatePersistenceTests.batch(7, 8, 2);
        var complete = CatalogUpdatePersistenceTests.batch(7, 10, 2);
        when(changes.readSince(7)).thenReturn(partial, complete);
        when(repository.apply(any(), same(complete), any())).thenReturn(state(10, UUID.randomUUID()));
        assertThat(useCase().process(CatalogUpdatePersistenceTests.notice("ahead", 10)).recorded()).isTrue();
        verify(repository, never()).apply(any(), same(partial), any());
        verify(changes, times(2)).readSince(7);
    }

    @Test void anUnusableTechnicalSessionStopsWorkAndInvalidatesAccess() {
        when(access.getToken()).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "FICTIONAL_EXPIRED"));
        var result = useCase().process(CatalogUpdatePersistenceTests.notice("expired", 10));
        assertThat(result.terminal()).isTrue();
        assertThat(result.recorded()).isFalse();
        verify(access).invalidate();
        verify(repository, never()).getState();
        verifyNoInteractions(changes);
    }

    @Test void noConfirmedCopyCannotBeUsedAsAnIncrementalBase() {
        when(repository.getState()).thenReturn(new CatalogState(null, null, null, null, null,
                SynchronizationPhase.NOT_STARTED, null, "NOT_STARTED"));
        var result = useCase().process(CatalogUpdatePersistenceTests.notice("no-copy", 10));
        assertThat(result.recorded()).isFalse();
        assertThat(result.terminal()).isTrue();
        verifyNoInteractions(changes, recovery);
    }

    @Test void failingToPersistTheFailureStateReturnsUnverifiableInsteadOfAnAcknowledgment() {
        when(changes.readSince(7)).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "FICTIONAL_NETWORK"));
        when(repository.report(eq(SynchronizationPhase.FAILED), anyString()))
                .thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "FICTIONAL_LOCAL_UNAVAILABLE"));
        var result = useCase().process(CatalogUpdatePersistenceTests.notice("unknown-state", 10));
        assertThat(result.recorded()).isFalse();
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
        assertThat(result.safeDiagnostic()).isEqualTo("CATALOG_RESULT_NOT_PERSISTED");
    }

    @Test void interruptionDoesNotStartAnotherIncrementalAttempt() {
        try {
            Thread.currentThread().interrupt();
            var result = useCase().check();
            assertThat(result.phase()).isEqualTo(SynchronizationPhase.FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(changes);
            verify(repository, never()).apply(any(), any(), any());
        } finally { Thread.interrupted(); }
    }

    @Test void aPreviouslyCommittedRejectionCanBeAcknowledgedWithoutWritingAgain() {
        var rejection = new RejectedCatalogRecord("fictional-topic", 0, 3, "INCOMPATIBLE_CATALOG_NOTIFICATION");
        when(repository.isRejected(rejection)).thenReturn(true);
        assertThat(useCase().reject(rejection).recorded()).isTrue();
        verify(repository, never()).recordRejection(any());
        verifyNoInteractions(changes, recovery);
    }

    @Test void recoveryBehindTheNotificationDoesNotCreateAnEarlyReceipt() {
        when(recovery.resume()).thenReturn(Optional.of(new SynchronizationResult(SynchronizationPhase.SNAPSHOT_APPLIED,
                1, "SNAPSHOT_APPLIED")));
        var result = useCase().process(CatalogUpdatePersistenceTests.notice("ahead-of-snapshot", 10));
        assertThat(result.recorded()).isFalse();
        assertThat(result.safeDiagnostic()).isEqualTo("NOTIFICATION_NOT_COVERED");
        verify(repository, never()).recordCovered(any());
        verifyNoInteractions(changes);
    }

    @Test void onlyASuccessfullyCommittedCheckSatisfiesCurrentExecutionVerification() {
        var publication = CatalogUpdatePersistenceTests.batch(7, 7, 1);
        when(changes.readSince(7)).thenReturn(publication);
        when(repository.checked(publication.publication())).thenReturn(state(7, null));
        assertThat(lock.verificationRequired()).isTrue();
        useCase().check();
        assertThat(lock.verificationRequired()).isFalse();
    }

    @Test void duplicateAndRejectedMessagesDoNotCreateAVerification() {
        when(repository.isProcessed("duplicate")).thenReturn(true);
        var useCase = useCase();
        useCase.process(CatalogUpdatePersistenceTests.notice("duplicate", 8));
        useCase.reject(new RejectedCatalogRecord("fictional-topic", 0, 4, "INCOMPATIBLE_CATALOG_NOTIFICATION"));
        assertThat(lock.verificationRequired()).isTrue();
        verifyNoInteractions(changes);
    }

    @Test void failureInvalidatesCurrentExecutionEvenWhenItsDiagnosticCannotBePersisted() {
        lock.confirmVerification();
        when(changes.readSince(7)).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "FICTIONAL_NETWORK"));
        when(repository.report(eq(SynchronizationPhase.FAILED), anyString()))
                .thenThrow(new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "FICTIONAL_STORAGE"));
        assertThat(useCase().check().phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
        assertThat(lock.verificationRequired()).isTrue();
        assertThat(lock.trySynchronize()).isTrue();
        lock.endSynchronization();
    }

    @Test void usesConfiguredAttemptsAndCooldownBeforeAnotherIncrementalCycle() {
        when(changes.readSince(7)).thenThrow(new CatalogSnapshotException(
                SnapshotFailureKind.TEMPORARY, "FICTIONAL_TEMPORARY"));
        var operation = new UpdateCatalogUseCaseImpl(changes, repository, recovery, access, clock,
                2, Duration.ZERO, Duration.ofMillis(750), lock);
        assertThat(operation.check().retryAt()).isEqualTo(SnapshotFixtures.TIME.plusMillis(750));
        operation.check();
        verify(changes, times(2)).readSince(7);
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusMillis(750));
        operation.check();
        verify(changes, times(4)).readSince(7);
        verify(repository, never()).apply(any(), any(), any());
    }

}
