package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;
import java.util.UUID;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;

/**
 * Metadata persistida: aplicación confirmada y último intento son datos distintos.
 * Una versión nula significa que todavía no hay copia, incluso si las tablas están vacías.
 */
public record CatalogState(Long appliedVersion, UUID appliedRunId, Instant generatedAt,
        Instant appliedAt, UUID lastRunId, SynchronizationPhase phase, Instant attemptedAt,
        String safeDiagnostic, Long observedVersion, Instant lastCheckedAt) {

    /** Metadata anterior al seguimiento, con comprobación remota todavía desconocida. */
    public CatalogState(Long appliedVersion, UUID appliedRunId, Instant generatedAt,
            Instant appliedAt, UUID lastRunId, SynchronizationPhase phase, Instant attemptedAt,
            String safeDiagnostic) {
        this(appliedVersion, appliedRunId, generatedAt, appliedAt, lastRunId, phase, attemptedAt,
                safeDiagnostic, null, null);
    }

    /** @return si existe una copia completa confirmada, aunque esté vacía o atrasada */
    public boolean hasCopy() {
        return appliedVersion != null;
    }
}
