package com.prog2.catalog.synchronization.application.usecases;

import java.time.*;
import java.util.Optional;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
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
    /**
     * Presupuesto y pausas de recuperación, sin dependencias de configuración o infraestructura.
     * @param maxCycles cantidad máxima de ciclos por bloque
     * @param cycleDelay espera entre ciclos recuperables
     * @param blockDelay espera después de agotar el bloque
     */
    public record RecoverySettings(int maxCycles, Duration cycleDelay, Duration blockDelay) { }

    private final SynchronizeCatalogUseCase snapshots;
    private final CatalogUpdateRepository repository;
    private final TechnicalAccessGateway access;
    private final Clock clock;
    private final CatalogSynchronizationGuard lock;
    private final RecoverySettings recoverySettings;
    private CatalogRecoveryReason pending;
    private Instant nextAttempt;
    private int cycles;
    private SynchronizationResult stopped;

    public RecoverCatalogUseCaseImpl(SynchronizeCatalogUseCase snapshots, CatalogUpdateRepository repository,
            TechnicalAccessGateway access, Clock clock, RecoverySettings recoverySettings,
            CatalogSynchronizationGuard lock) {
        this.snapshots = snapshots;
        this.repository = repository;
        this.access = access;
        this.clock = clock;
        this.lock = lock;
        this.recoverySettings = recoverySettings;
    }

    @Override
    public SynchronizationResult recover(CatalogRecoveryReason reason) {
        if (!lock.trySynchronize()) { return busy(); }
        try {
            if (pending == null) { pending = reason; }
            lock.requireVerification();
            return attempt();
        } finally { lock.endSynchronization(); }
    }

    @Override
    public Optional<SynchronizationResult> resume() {
        if (!lock.trySynchronize()) { return Optional.of(busy()); }
        try {
            return pending == null ? Optional.empty() : Optional.of(attempt());
        } finally { lock.endSynchronization(); }
    }

    private SynchronizationResult attempt() {
        if (stopped != null) { return stopped; }
        access.getToken();
        if (nextAttempt != null && clock.instant().isBefore(nextAttempt)) { return waiting(); }
        if (Thread.currentThread().isInterrupted()) { return busy(); }
        if (cycles == recoverySettings.maxCycles()) { cycles = 0; }
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
            boolean exhausted = cycles == recoverySettings.maxCycles();
            nextAttempt = clock.instant().plus(exhausted ? recoverySettings.blockDelay() : recoverySettings.cycleDelay());
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
