package com.prog2.catalog.infrastructure.startup;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogConnectionSettingsGateway;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogSnapshotRepository;

import static org.mockito.Mockito.*;

class CatalogSynchronizationMonitorTests {
    @Test
    void schedulesWithTheConfiguredDelayInMilliseconds() {
        var clients = mock(CatalogRemoteClients.class);
        var scheduler = mock(ScheduledExecutorService.class);
        try (var monitor = new CatalogSynchronizationMonitor(clients,
                mock(CatalogConnectionSettingsGateway.class), mock(CatalogSnapshotRepository.class),
                mock(UpdateCatalogUseCase.class), scheduler, Duration.ofMillis(750))) {
            monitor.start();
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(750L), eq(TimeUnit.MILLISECONDS));
        }
        verify(scheduler).shutdownNow();
    }
}
