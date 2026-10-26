package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;
import com.prog2.catalog.synchronization.domain.enums.*;

/** recorded permite confirmar el offset; significa recibo/rechazo durable, no solo datos leídos. */
public record CatalogUpdateResult(SynchronizationPhase phase, boolean recorded, Instant retryAt,
        String safeDiagnostic, SnapshotFailureKind failureKind) {
    public boolean terminal() {
        return failureKind == SnapshotFailureKind.TERMINAL || failureKind == SnapshotFailureKind.UNAUTHORIZED;
    }
}
