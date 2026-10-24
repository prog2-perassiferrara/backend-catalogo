package com.prog2.catalog.synchronization.application.usecases;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/**
 * CU-02: parte de metadata confirmada, no del orden de notificaciones.
 * El estado privado del intento retiene candidato/runId para reconciliar una escritura incierta.
 * No mantiene transacciones abiertas durante Redis, pausas ni reconstrucción.
 */
public class UpdateCatalogUseCaseImpl implements UpdateCatalogUseCase {
    private final CatalogChangesGateway changes;
    private final CatalogUpdateRepository repository;
    private final CatalogSnapshotRepository snapshots;
    private final RecoverCatalogUseCase recovery;
    private final TechnicalAccessGateway access;
    private final Clock clock;
    private final Duration retryDelay;
    private final ReentrantLock lock;
    private Instant nextAttempt;
    private CatalogUpdateResult stopped;

    public UpdateCatalogUseCaseImpl(CatalogChangesGateway changes, CatalogUpdateRepository repository,
            CatalogSnapshotRepository snapshots, RecoverCatalogUseCase recovery, TechnicalAccessGateway access,
            Clock clock, Duration retryDelay, ReentrantLock lock) {
        this.changes = changes;
        this.repository = repository;
        this.snapshots = snapshots;
        this.recovery = recovery;
        this.access = access;
        this.clock = clock;
        this.retryDelay = retryDelay;
        this.lock = lock;
    }

    @Override
    public CatalogUpdateResult process(CatalogNotification notification) { return execute(notification); }

    @Override
    public SynchronizationResult check() {
        var result = execute(null);
        return new SynchronizationResult(result.phase(), 0, result.safeDiagnostic(), result.failureKind(), result.retryAt());
    }

    @Override
    public CatalogUpdateResult reject(RejectedCatalogRecord rejection) {
        if (!lock.tryLock()) { return busy(); }
        try {
            if (stopped != null) { return stopped; }
            access.getToken();
            if (!repository.isRejected(rejection)) { repository.recordRejection(rejection); }
            return recorded("CATALOG_RECORD_REJECTED");
        } catch (CatalogSnapshotException failure) {
            return failed(failure, Duration.ofSeconds(30));
        } finally { lock.unlock(); }
    }

    private CatalogUpdateResult execute(CatalogNotification notification) {
        if (!lock.tryLock()) { return busy(); }
        try {
            if (stopped != null) { return stopped; }
            access.getToken();
            if (notification != null && repository.isProcessed(notification.eventId())) { return recorded("CATALOG_DUPLICATE"); }
            CatalogState state = snapshots.getState();
            if (!state.hasCopy()) {
                return failed(new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "CATALOG_COPY_UNAVAILABLE"), Duration.ZERO);
            }
            if (notification != null && state.appliedVersion() >= notification.newVersion()) {
                repository.recordCovered(notification);
                return recorded("CATALOG_EVENT_COVERED");
            }
            var pending = recovery.resume();
            if (pending.isPresent()) { return afterRecovery(pending.get(), notification); }
            if (nextAttempt != null && clock.instant().isBefore(nextAttempt)) { return waiting("CATALOG_RETRY_WAIT", nextAttempt); }
            return run(new UpdateCycle(notification));
        } catch (CatalogSnapshotException failure) {
            return failed(failure, Duration.ofSeconds(30));
        } finally { lock.unlock(); }
    }

    private CatalogUpdateResult run(UpdateCycle cycle) {
        repository.report(SynchronizationPhase.IN_PROGRESS, "CATALOG_UPDATE_IN_PROGRESS");
        for (int attempt = 1; attempt <= 3 && !Thread.currentThread().isInterrupted(); attempt++) {
            try {
                return applyAttempt(cycle);
            } catch (CatalogDiscontinuityException failure) {
                return afterRecovery(recovery.recover(failure.reason()), cycle.notification);
            } catch (CatalogSnapshotException failure) {
                if (failure.kind() == SnapshotFailureKind.UNAUTHORIZED || failure.kind() == SnapshotFailureKind.TERMINAL) {
                    return failed(failure, Duration.ZERO);
                }
                if (!canRetry(cycle, failure, attempt)) { return finish(cycle); }
            }
        }
        return finish(cycle);
    }

    private boolean canRetry(UpdateCycle cycle, CatalogSnapshotException failure, int attempt) {
        if (cycle.writing && failure.kind() == SnapshotFailureKind.UNCERTAIN) { cycle.uncertain = true; }
        cycle.failure = failure;
        return attempt < 3 && pause();
    }

    /** La consulta fresca precede a cualquier repetición de una escritura incierta. */
    private CatalogUpdateResult applyAttempt(UpdateCycle cycle) {
        access.getToken();
        CatalogState state = snapshots.getState();
        if (cycle.uncertain) {
            cycle.uncertain = false;
            if (cycle.runId.equals(state.appliedRunId())) { return confirmed(cycle.notification, state); }
        }
        if (cycle.candidate == null) {
            cycle.candidate = changes.readSince(state.appliedVersion());
            cycle.candidate.validate();
        }
        if (cycle.notification != null && cycle.notification.newVersion() > cycle.candidate.publication().currentVersion()) {
            cycle.candidate = null;
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "NOTIFICATION_AHEAD_OF_PUBLICATION");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_INTERRUPTED");
        }
        if (cycle.candidate.baseVersion() == cycle.candidate.publication().currentVersion()) {
            state = repository.checked(cycle.candidate.publication());
        } else {
            cycle.writing = true;
            state = repository.apply(cycle.runId, cycle.candidate, cycle.notification);
        }
        nextAttempt = null;
        return confirmed(cycle.notification, state);
    }

    private CatalogUpdateResult finish(UpdateCycle cycle) {
        return failed(cycle.uncertain ? new CatalogSnapshotException(SnapshotFailureKind.UNCERTAIN,
                "CATALOG_COMMIT_UNVERIFIED") : cycle.failure, Duration.ofSeconds(30));
    }

    private CatalogUpdateResult confirmed(CatalogNotification notification, CatalogState state) {
        if (notification != null) {
            if (state.appliedVersion() < notification.newVersion()) { return waiting("NOTIFICATION_NOT_COVERED", clock.instant().plusSeconds(5)); }
            repository.recordCovered(notification);
        }
        return new CatalogUpdateResult(state.phase(), notification != null, null, "CATALOG_CHECKED", null);
    }

    private CatalogUpdateResult afterRecovery(SynchronizationResult result, CatalogNotification notification) {
        if (result.phase() == SynchronizationPhase.SNAPSHOT_APPLIED && notification != null) {
            return confirmed(notification, snapshots.getState());
        }
        if (result.failureKind() == SnapshotFailureKind.UNAUTHORIZED || result.failureKind() == SnapshotFailureKind.TERMINAL) {
            return failed(new CatalogSnapshotException(result.failureKind(), result.safeDiagnostic()), Duration.ZERO);
        }
        return new CatalogUpdateResult(result.phase(), false, result.retryAt(), result.safeDiagnostic(), result.failureKind());
    }

    private CatalogUpdateResult failed(CatalogSnapshotException failure, Duration delay) {
        nextAttempt = clock.instant().plus(delay);
        if (failure.kind() == SnapshotFailureKind.UNAUTHORIZED) { access.invalidate(); }
        SynchronizationPhase phase = failure.kind() == SnapshotFailureKind.UNCERTAIN
                ? SynchronizationPhase.UNVERIFIABLE : SynchronizationPhase.FAILED;
        CatalogUpdateResult result = new CatalogUpdateResult(phase, false, nextAttempt,
                failure.getMessage(), failure.kind());
        if (result.terminal()) { stopped = result; }
        try { repository.report(phase, failure.getMessage()); }
        catch (CatalogSnapshotException _) {
            return new CatalogUpdateResult(SynchronizationPhase.UNVERIFIABLE, false, nextAttempt,
                    "CATALOG_RESULT_NOT_PERSISTED", result.terminal() ? result.failureKind() : SnapshotFailureKind.UNCERTAIN);
        }
        return result;
    }

    private boolean pause() {
        try { Thread.sleep(retryDelay); return true; }
        catch (InterruptedException _) { Thread.currentThread().interrupt(); return false; }
    }

    private CatalogUpdateResult busy() { return waiting("CATALOG_BUSY", clock.instant().plusSeconds(5)); }
    private static CatalogUpdateResult waiting(String code, Instant next) {
        return new CatalogUpdateResult(SynchronizationPhase.RECOVERY_WAITING, false, next, code, SnapshotFailureKind.TEMPORARY);
    }
    private CatalogUpdateResult recorded(String code) {
        return new CatalogUpdateResult(snapshots.getState().phase(), true, null, code, null);
    }

    private static final class UpdateCycle {
        private final UUID runId = UUID.randomUUID();
        private final CatalogNotification notification;
        private CatalogChangesBatch candidate;
        private boolean uncertain;
        private boolean writing;
        private CatalogSnapshotException failure = new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_INTERRUPTED");
        private UpdateCycle(CatalogNotification notification) { this.notification = notification; }
    }
}
