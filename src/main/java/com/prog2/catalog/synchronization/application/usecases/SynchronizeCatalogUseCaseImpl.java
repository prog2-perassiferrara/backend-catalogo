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
 * Coordina una carga completa. El bloqueo cubre todo el ciclo, no mantiene abierta una transacción.
 * Los tres intentos comparten candidato y runId; HTTP y las pausas ocurren fuera de MySQL.
 */
public class SynchronizeCatalogUseCaseImpl implements SynchronizeCatalogUseCase {
    private static final int MAX_ATTEMPTS = 3;
    private final CatalogSnapshotGateway snapshots;
    private final CatalogSnapshotRepository repository;
    private final TechnicalAccessGateway access;
    private final Clock clock;
    private final Duration retryDelay;
    private final ReentrantLock cycleLock = new ReentrantLock();

    public SynchronizeCatalogUseCaseImpl(CatalogSnapshotGateway snapshots, CatalogSnapshotRepository repository,
            TechnicalAccessGateway access, Clock clock, Duration retryDelay) {
        this.snapshots = snapshots;
        this.repository = repository;
        this.access = access;
        this.clock = clock;
        this.retryDelay = retryDelay;
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
        CatalogSnapshot candidate = null;
        boolean registered = false;
        boolean uncertain = false;
        String diagnostic = "SNAPSHOT_INTERRUPTED";
        int attempts = 0;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            attempts = attempt;
            boolean writing = false;
            try {
                if (uncertain) {
                    if (runId.equals(repository.getState().appliedRunId())) {
                        return applied(attempt);
                    }
                    uncertain = false;
                }
                if (!registered) {
                    repository.beginAttempt(runId, clock.instant());
                    registered = true;
                }
                if (candidate == null) {
                    candidate = snapshots.fetch(access.getToken());
                    candidate.validate();
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                writing = true;
                repository.replace(runId, candidate);
                return applied(attempt);
            } catch (CatalogSnapshotException failure) {
                diagnostic = failure.getMessage();
                // Una lectura de reconciliación fallida no borra la incertidumbre anterior.
                uncertain = uncertain || writing && failure.kind() == SnapshotFailureKind.UNCERTAIN;
                if (failure.kind() == SnapshotFailureKind.UNAUTHORIZED) {
                    access.invalidate();
                    break;
                }
                if (failure.kind() == SnapshotFailureKind.TERMINAL) {
                    break;
                }
            }
            if (attempt < MAX_ATTEMPTS && !pause()) {
                diagnostic = "SNAPSHOT_INTERRUPTED";
                break;
            }
        }
        if (uncertain) {
            try {
                if (runId.equals(repository.getState().appliedRunId())) {
                    return applied(attempts);
                }
                uncertain = false;
            } catch (CatalogSnapshotException _) {
                // No presentar un rollback ni un éxito que no se pudo verificar.
            }
        }
        return finish(runId, uncertain ? SynchronizationPhase.UNVERIFIABLE : SynchronizationPhase.FAILED,
                attempts, diagnostic);
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

    private SynchronizationResult finish(UUID runId, SynchronizationPhase phase, int attempts, String diagnostic) {
        try {
            CatalogState state = repository.finishAttempt(runId, phase, diagnostic);
            if (runId.equals(state.appliedRunId())) {
                return applied(attempts);
            }
        } catch (CatalogSnapshotException _) {
            return new SynchronizationResult(SynchronizationPhase.UNVERIFIABLE, attempts,
                    "SNAPSHOT_RESULT_NOT_PERSISTED");
        }
        return new SynchronizationResult(phase, attempts, diagnostic);
    }

    private static SynchronizationResult applied(int attempts) {
        return new SynchronizationResult(SynchronizationPhase.SNAPSHOT_APPLIED, attempts, "SNAPSHOT_APPLIED");
    }
}
