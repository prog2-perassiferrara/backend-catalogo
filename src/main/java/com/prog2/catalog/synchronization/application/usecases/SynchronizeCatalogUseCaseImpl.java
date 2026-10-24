package com.prog2.catalog.synchronization.application.usecases;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/**
 * Coordina una carga completa. El bloqueo cubre el ciclo completo, no mantiene abierta una transacción.
 * Los tres intentos comparten candidato y runId; HTTP y las pausas ocurren fuera de MySQL.
 */
public class SynchronizeCatalogUseCaseImpl implements SynchronizeCatalogUseCase {
    private static final int MAX_ATTEMPTS = 3;
    private final CatalogSnapshotGateway snapshots;
    private final CatalogSnapshotRepository repository;
    private final TechnicalAccessGateway access;
    private final Clock clock;
    private final Duration retryDelay;
    private final ReentrantLock cycleLock;

    public SynchronizeCatalogUseCaseImpl(CatalogSnapshotGateway snapshots, CatalogSnapshotRepository repository,
            TechnicalAccessGateway access, Clock clock, Duration retryDelay, ReentrantLock cycleLock) {
        this.snapshots = snapshots;
        this.repository = repository;
        this.access = access;
        this.clock = clock;
        this.retryDelay = retryDelay;
        this.cycleLock = cycleLock;
    }

    @Override
    public SynchronizationResult synchronize() {
        if (!cycleLock.tryLock()) {
            return new SynchronizationResult(SynchronizationPhase.BUSY, 0, "SNAPSHOT_BUSY");
        }
        try {
            return runCycle(UUID.randomUUID());
        } finally {
            cycleLock.unlock();
        }
    }

    private SynchronizationResult runCycle(UUID runId) {
        SnapshotCycle cycle = new SnapshotCycle(runId);
        while (cycle.attempts < MAX_ATTEMPTS && !Thread.currentThread().isInterrupted()) {
            cycle.attempts++;
            if (applyAttempt(cycle)) {
                return applied(cycle.attempts);
            }
            if (!retry(cycle)) {
                break;
            }
        }
        return finishCycle(cycle);
    }

    /** Cada intento verifica un commit incierto antes de permitir otra escritura. */
    private boolean applyAttempt(SnapshotCycle cycle) {
        boolean writing = false;
        try {
            if (reconcile(cycle)) {
                return true;
            }
            prepareCandidate(cycle);
            if (Thread.currentThread().isInterrupted()) {
                cycle.retryable = false;
                return false;
            }
            writing = true;
            repository.replace(cycle.runId, cycle.candidate);
            return true;
        } catch (CatalogSnapshotException failure) {
            registerFailure(cycle, failure, writing);
            return false;
        }
    }

    private void prepareCandidate(SnapshotCycle cycle) {
        if (!cycle.registered) {
            repository.beginAttempt(cycle.runId, clock.instant());
            cycle.registered = true;
        }
        if (cycle.candidate == null) {
            cycle.candidate = snapshots.fetch(access.getToken());
            cycle.candidate.validate();
        }
    }

    /** Una lectura fallida conserva la incertidumbre e impide volver a escribir sin verificar. */
    private boolean reconcile(SnapshotCycle cycle) {
        if (!cycle.uncertain) {
            return false;
        }
        boolean confirmed = cycle.runId.equals(repository.getState().appliedRunId());
        cycle.uncertain = false;
        return confirmed;
    }

    private void registerFailure(SnapshotCycle cycle, CatalogSnapshotException failure, boolean writing) {
        cycle.diagnostic = failure.getMessage();
        cycle.failureKind = failure.kind();
        if (writing && failure.kind() == SnapshotFailureKind.UNCERTAIN) {
            cycle.uncertain = true;
        }
        cycle.retryable = failure.kind() == SnapshotFailureKind.TEMPORARY
                || failure.kind() == SnapshotFailureKind.UNCERTAIN;
        if (failure.kind() == SnapshotFailureKind.UNAUTHORIZED) {
            access.invalidate();
        }
    }

    private boolean retry(SnapshotCycle cycle) {
        if (!cycle.retryable || cycle.attempts == MAX_ATTEMPTS) {
            return false;
        }
        if (pause()) {
            return true;
        }
        cycle.diagnostic = "SNAPSHOT_INTERRUPTED";
        return false;
    }

    private SynchronizationResult finishCycle(SnapshotCycle cycle) {
        try {
            if (reconcile(cycle)) {
                return applied(cycle.attempts);
            }
        } catch (CatalogSnapshotException _) {
            // No presentar un rollback ni un éxito que no se pudo verificar.
        }
        return finish(cycle.runId,
                cycle.uncertain ? SynchronizationPhase.UNVERIFIABLE : SynchronizationPhase.FAILED,
                cycle.attempts, cycle.diagnostic, cycle.failureKind);
    }

    private boolean pause() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SynchronizationResult finish(UUID runId, SynchronizationPhase phase, int attempts, String diagnostic,
            SnapshotFailureKind failureKind) {
        try {
            CatalogState state = repository.finishAttempt(runId, phase, diagnostic);
            if (runId.equals(state.appliedRunId())) {
                return applied(attempts);
            }
        } catch (CatalogSnapshotException _) {
            return new SynchronizationResult(SynchronizationPhase.UNVERIFIABLE, attempts,
                    "SNAPSHOT_RESULT_NOT_PERSISTED", SnapshotFailureKind.UNCERTAIN);
        }
        return new SynchronizationResult(phase, attempts, diagnostic, failureKind);
    }

    private static SynchronizationResult applied(int attempts) {
        return new SynchronizationResult(SynchronizationPhase.SNAPSHOT_APPLIED, attempts, "SNAPSHOT_APPLIED");
    }

    /** Estado privado de una ejecución; no se comparte entre llamadas ni se conserva al finalizar. */
    private static final class SnapshotCycle {
        private final UUID runId;
        private CatalogSnapshot candidate;
        private boolean registered;
        private boolean uncertain;
        private boolean retryable = true;
        private int attempts;
        private String diagnostic = "SNAPSHOT_INTERRUPTED";
        private SnapshotFailureKind failureKind = SnapshotFailureKind.TEMPORARY;

        private SnapshotCycle(UUID runId) {
            this.runId = runId;
        }
    }
}
