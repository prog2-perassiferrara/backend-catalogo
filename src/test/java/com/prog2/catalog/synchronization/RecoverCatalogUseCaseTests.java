package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.prog2.catalog.synchronization.application.usecases.RecoverCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.SynchronizationResult;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoverCatalogUseCaseTests {
    private final SynchronizeCatalogUseCase snapshots = mock(SynchronizeCatalogUseCase.class);
    private final CatalogUpdateRepository repository = mock(CatalogUpdateRepository.class);
    private final TechnicalAccessGateway access = mock(TechnicalAccessGateway.class);
    private final Clock clock = mock(Clock.class);

    private RecoverCatalogUseCaseImpl recovery() {
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        return new RecoverCatalogUseCaseImpl(snapshots, repository, access, clock, new ReentrantLock());
    }

    @Test
    void respectsThreeCyclesAndTheFiveMinutePauseEvenWhenMoreMessagesArrive() {
        var recovery = recovery();
        when(snapshots.synchronize()).thenReturn(new SynchronizationResult(SynchronizationPhase.FAILED, 3,
                "FICTIONAL_TEMPORARY", SnapshotFailureKind.TEMPORARY));
        assertThat(recovery.resume()).isEmpty();
        assertThat(recovery.recover(CatalogRecoveryReason.CHANGE_MISSING).retryAt()).isEqualTo(SnapshotFixtures.TIME.plusSeconds(30));
        for (int i=0; i<5; i++) { recovery.recover(CatalogRecoveryReason.PUBLICATION_CHANGED); }
        verify(snapshots).synchronize();
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(29));
        recovery.resume();
        verify(snapshots).synchronize();
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(30));
        recovery.resume();
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(60));
        assertThat(recovery.resume().orElseThrow().retryAt()).isEqualTo(SnapshotFixtures.TIME.plusSeconds(360));
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(359));
        recovery.recover(CatalogRecoveryReason.CHANGE_MISSING);
        verify(snapshots, times(3)).synchronize();
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(360));
        when(snapshots.synchronize()).thenReturn(new SynchronizationResult(SynchronizationPhase.SNAPSHOT_APPLIED, 1, "SNAPSHOT_APPLIED"));
        assertThat(recovery.resume().orElseThrow().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(recovery.resume()).isEmpty();
        verify(snapshots, times(4)).synchronize();
    }

    @ParameterizedTest
    @EnumSource(value=SnapshotFailureKind.class, names={"TERMINAL","UNAUTHORIZED"})
    void neverStartsAnotherCycleForPermanentFailure(SnapshotFailureKind kind) {
        var recovery = recovery();
        when(snapshots.synchronize()).thenReturn(new SynchronizationResult(SynchronizationPhase.FAILED, 1,
                "FICTIONAL_PERMANENT", kind));
        assertThat(recovery.recover(CatalogRecoveryReason.INVALID_REMOTE_DATA).failureKind()).isEqualTo(kind);
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(3600));
        recovery.resume();
        recovery.recover(CatalogRecoveryReason.CHANGE_MISSING);
        verify(snapshots).synchronize();
    }
}
