package com.prog2.catalog.infrastructure.startup;

import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.CatalogConnectionSettings;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/** Seguimiento independiente de Kafka, con ejecución única y demora fija después de cada comprobación. */
public class CatalogSynchronizationMonitor implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSynchronizationMonitor.class);
    private final CatalogRemoteClients clients;
    private final CatalogConnectionSettingsGateway settings;
    private final CatalogSnapshotRepository snapshots;
    private final UpdateCatalogUseCase updates;
    private final ScheduledExecutorService scheduler;
    private final Duration interval;
    private Future<?> task;
    private boolean closed;

    public CatalogSynchronizationMonitor(CatalogRemoteClients clients, CatalogConnectionSettingsGateway settings,
            CatalogSnapshotRepository snapshots, UpdateCatalogUseCase updates, ScheduledExecutorService scheduler, Duration interval) {
        this.clients = clients;
        this.settings = settings;
        this.snapshots = snapshots;
        this.updates = updates;
        this.scheduler = scheduler;
        this.interval = interval;
    }

    public synchronized void start() {
        if (!closed && task == null) { task = scheduler.scheduleWithFixedDelay(this::check, 0, interval.toMillis(), TimeUnit.MILLISECONDS); }
    }

    /** Atrapa fallos en la frontera del scheduler para no cancelar silenciosamente las ejecuciones posteriores. */
    private void check() {
        try {
            if (!snapshots.getState().hasCopy()) { return; }
            var configuration = settings.getSettings();
            startClients(configuration);
            var result = updates.check();
            LOGGER.info("Catalog tracking: {} ({})", result.phase(), result.safeDiagnostic());
            if (result.failureKind() == SnapshotFailureKind.TERMINAL || result.failureKind() == SnapshotFailureKind.UNAUTHORIZED) {
                clients.stopConsumer();
                scheduler.shutdown();
            }
        } catch (CatalogSnapshotException failure) {
            LOGGER.warn("Catalog tracking unavailable: {}", failure.getMessage());
            if (failure.kind() == SnapshotFailureKind.UNAUTHORIZED || failure.kind() == SnapshotFailureKind.TERMINAL) {
                clients.stopConsumer();
                scheduler.shutdown();
            }
        } catch (RuntimeException _) {
            LOGGER.error("CATALOG_TRACKING_UNVERIFIABLE");
        }
    }

    private void startClients(CatalogConnectionSettings configuration) {
        try { clients.start(configuration, updates); }
        catch (RuntimeException _) {
            // El arranque de Kafka no debe impedir comprobar Redis si este ya está preparado.
            LOGGER.warn("CATALOG_REMOTE_CLIENT_START_UNAVAILABLE");
        }
    }

    @Override public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
        clients.stopConsumer();
    }
}
