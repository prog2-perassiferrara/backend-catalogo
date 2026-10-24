package com.prog2.catalog.synchronization.application.usecases;

import java.time.*;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.SynchronizationResult;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/**
 * Ejecuta como máximo un ciclo de snapshot por entrada. Los contadores y la próxima
 * ejecución pertenecen a la recuperación pendiente, no a la notificación que la descubre.
 * Todas las entradas toman el mismo bloqueo reentrante que CU-01 y CU-02.
 */
public class RecoverCatalogUseCaseImpl implements RecoverCatalogUseCase {
    private final SynchronizeCatalogUseCase snapshots;
    private final CatalogUpdateRepository repository;
    private final TechnicalAccessGateway access;
    private final Clock clock;
    private final ReentrantLock lock;
    private CatalogRecoveryReason pending;
    private Instant nextAttempt;
    private int cycles;
    private SynchronizationResult stopped;

    public RecoverCatalogUseCaseImpl(SynchronizeCatalogUseCase snapshots, CatalogUpdateRepository repository,
            TechnicalAccessGateway access, Clock clock, ReentrantLock lock) {
        this.snapshots = snapshots;
        this.repository = repository;
        this.access = access;
        this.clock = clock;
        this.lock = lock;
    }

    @Override
    public SynchronizationResult recover(CatalogRecoveryReason reason) {
        if (!lock.tryLock()) { return busy(); }
        try {
            if (pending == null) { pending = reason; }
            return attempt();
        } finally { lock.unlock(); }
    }

    @Override
    public Optional<SynchronizationResult> resume() {
        if (!lock.tryLock()) { return Optional.of(busy()); }
        try {
            return pending == null ? Optional.empty() : Optional.of(attempt());
        } finally { lock.unlock(); }
    }

    private SynchronizationResult attempt() {
        if (stopped != null) { return stopped; }
        access.getToken();
        if (nextAttempt != null && clock.instant().isBefore(nextAttempt)) { return waiting(); }
        if (Thread.currentThread().isInterrupted()) { return busy(); }
        if (cycles == 3) { cycles = 0; }
        SynchronizationResult result = snapshots.synchronize();
        if (result.phase() == SynchronizationPhase.BUSY) { return result; }
        if (result.phase() == SynchronizationPhase.SNAPSHOT_APPLIED) {
            pending = null;
            cycles = 0;
            nextAttempt = null;
            return result;
        }
        if (result.failureKind() == SnapshotFailureKind.TEMPORARY || result.failureKind() == SnapshotFailureKind.UNCERTAIN) {
            cycles++;
            boolean exhausted = cycles == 3;
            nextAttempt = clock.instant().plus(exhausted ? Duration.ofMinutes(5) : Duration.ofSeconds(30));
            repository.report(SynchronizationPhase.RECOVERY_WAITING, "RECOVERY_" + pending.name());
            return waiting();
        }
        stopped = result.failureKind() == null ? new SynchronizationResult(SynchronizationPhase.FAILED,
                result.attempts(), result.safeDiagnostic(), SnapshotFailureKind.TERMINAL) : result;
        return stopped;
    }

    private SynchronizationResult waiting() {
        return new SynchronizationResult(SynchronizationPhase.RECOVERY_WAITING, cycles,
                "RECOVERY_" + pending.name(), SnapshotFailureKind.TEMPORARY, nextAttempt);
    }

    private static SynchronizationResult busy() {
        return new SynchronizationResult(SynchronizationPhase.BUSY, 0, "CATALOG_BUSY");
    }
}
