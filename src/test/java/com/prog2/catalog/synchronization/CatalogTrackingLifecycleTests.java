package com.prog2.catalog.synchronization;

import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controla el reloj del scheduler en su frontera; ejecuta el monitor real sin esperar ticks. */
class CatalogTrackingLifecycleTests {
    private final CatalogRemoteClients clients = mock(CatalogRemoteClients.class);
    private final CatalogConnectionSettingsGateway settings = mock(CatalogConnectionSettingsGateway.class);
    private final CatalogSnapshotRepository snapshots = mock(CatalogSnapshotRepository.class);
    private final UpdateCatalogUseCase updates = mock(UpdateCatalogUseCase.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final CatalogSynchronizationMonitor monitor = new CatalogSynchronizationMonitor(
            clients, settings, snapshots, updates, scheduler);
    private Runnable tick;

    @BeforeEach void prepare() {
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(30L), eq(TimeUnit.SECONDS)))
                .thenAnswer(call -> { tick = call.getArgument(0); return mock(ScheduledFuture.class); });
        when(snapshots.getState()).thenReturn(copy(7L));
        when(settings.getSettings()).thenReturn(new CatalogConnectionSettings("redis.test", 6379, "fictional-user",
                "fictional-redis-access", "catedra:sync:*", "kafka.test:9092", "fictional-group", "fictional-topic"));
        when(updates.check()).thenReturn(new SynchronizationResult(SynchronizationPhase.CURRENT, 0, "CATALOG_CHECKED"));
    }

    @AfterEach void close() { monitor.close(); }

    private static CatalogState copy(Long version) {
        return new CatalogState(version, null, SnapshotFixtures.TIME, SnapshotFixtures.TIME, null,
                SynchronizationPhase.SNAPSHOT_APPLIED, SnapshotFixtures.TIME, "FICTIONAL_COPY");
    }

    @Test void doesNotReadRemoteConfigurationOrStartClientsWithoutACopy() {
        when(snapshots.getState()).thenReturn(copy(null));
        monitor.start();
        tick.run();
        verifyNoInteractions(settings, clients, updates);
    }

    @Test void anEmptyVersionedCopyEnablesCheckingAndStartDoesNotDuplicateTheSchedule() {
        when(snapshots.getState()).thenReturn(copy(0L));
        monitor.start();
        monitor.start();
        tick.run();
        verify(updates).check();
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(30L), eq(TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @EnumSource(value = SnapshotFailureKind.class, names = {"TERMINAL", "UNAUTHORIZED"})
    void stopsTheConsumerAndSchedulingAfterAPermanentCheckFailure(SnapshotFailureKind kind) {
        when(updates.check()).thenReturn(new SynchronizationResult(SynchronizationPhase.FAILED, 1, "FICTIONAL_FAILURE", kind));
        monitor.start();
        tick.run();
        verify(clients).stopConsumer();
        verify(scheduler).shutdown();
    }

    @ParameterizedTest
    @EnumSource(value = SnapshotFailureKind.class, names = {"TEMPORARY", "TERMINAL", "UNAUTHORIZED"})
    void distinguishesTemporaryAndPermanentConfigurationFailures(SnapshotFailureKind kind) {
        when(settings.getSettings()).thenThrow(new CatalogSnapshotException(kind, "FICTIONAL_CONFIGURATION_FAILURE"));
        monitor.start();
        tick.run();
        verifyNoInteractions(updates);
        if (kind == SnapshotFailureKind.TEMPORARY) {
            verify(scheduler, never()).shutdown();
            verify(clients, never()).stopConsumer();
        } else {
            verify(scheduler).shutdown();
            verify(clients).stopConsumer();
        }
    }

    @Test void anUnexpectedExceptionDoesNotCancelSubsequentChecks() {
        when(updates.check()).thenThrow(new IllegalStateException("fictional-unexpected"))
                .thenReturn(new SynchronizationResult(SynchronizationPhase.CURRENT, 0, "CATALOG_CHECKED"));
        monitor.start();
        tick.run();
        tick.run();
        verify(updates, times(2)).check();
        verify(scheduler, never()).shutdown();
    }

    @Test void closingBeforeStartupPreventsSchedulingAndInterruptsOwnedWork() {
        monitor.close();
        monitor.start();
        verify(scheduler).shutdownNow();
        verify(clients).stopConsumer();
        verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
        verifyNoInteractions(settings, snapshots, updates);
    }
}
