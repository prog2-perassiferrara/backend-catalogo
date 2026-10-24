package com.prog2.catalog.synchronization.domain.model;

import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import java.time.Instant;

/** Resultado del ciclo actual, sin confundirlo con un intento histórico almacenado. */
public record SynchronizationResult(SynchronizationPhase phase, int attempts, String safeDiagnostic,
        SnapshotFailureKind failureKind, Instant retryAt) {
    /** Resultado sin fallo clasificado, por ejemplo éxito u ocupado. */
    public SynchronizationResult(SynchronizationPhase phase, int attempts, String safeDiagnostic) {
        this(phase, attempts, safeDiagnostic, null, null);
    }
    public SynchronizationResult(SynchronizationPhase phase, int attempts, String safeDiagnostic,
            SnapshotFailureKind failureKind) {
        this(phase, attempts, safeDiagnostic, failureKind, null);
    }
}
