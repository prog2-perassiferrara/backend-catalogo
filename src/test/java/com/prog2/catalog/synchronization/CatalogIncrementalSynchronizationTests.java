package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
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
    private final ReentrantLock lock=new ReentrantLock();
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        when(snapshots.getState()).thenReturn(state(7,null));
        when(recovery.resume()).thenReturn(Optional.empty());
    }
    private UpdateCatalogUseCaseImpl useCase() {
        return new UpdateCatalogUseCaseImpl(changes,repository,snapshots,recovery,access,clock,Duration.ZERO,lock);
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
        when(snapshots.getState()).thenReturn(state(7,null),state(7,null))
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
        var actualRecovery=new RecoverCatalogUseCaseImpl(load,repository,access,clock,lock);
        var useCase=new UpdateCatalogUseCaseImpl(changes,repository,snapshots,actualRecovery,access,clock,Duration.ZERO,lock);
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
        var snapshot=new SynchronizeCatalogUseCaseImpl(gateway,snapshots,access,clock,Duration.ZERO,lock);
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
}
