package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;

/** Fila única de control; el bloqueo de escritura protege datos y versión conjuntamente. */
@Entity
@Table(name = "catalog_sync_state")
public class CatalogSyncStateEntity {
    @Id
    private Long id;
    @Column(name = "applied_version")
    private Long appliedVersion;
    @Column(name = "applied_run_id", length = 36)
    private String appliedRunId;
    @Column(name = "generated_at")
    private Instant generatedAt;
    @Column(name = "applied_at")
    private Instant appliedAt;
    @Column(name = "last_run_id", length = 36)
    private String lastRunId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SynchronizationPhase phase;
    @Column(name = "attempted_at")
    private Instant attemptedAt;
    @Column(name = "safe_diagnostic", nullable = false, length = 100)
    private String safeDiagnostic;

    protected CatalogSyncStateEntity() { }

    public void begin(UUID runId, Instant now) {
        lastRunId = runId.toString();
        attemptedAt = now;
        phase = SynchronizationPhase.IN_PROGRESS;
        safeDiagnostic = "SNAPSHOT_IN_PROGRESS";
    }

    public void applied(UUID runId, long version, Instant generated, Instant now) {
        appliedVersion = version;
        appliedRunId = runId.toString();
        generatedAt = generated;
        appliedAt = now;
        finish(runId, SynchronizationPhase.SNAPSHOT_APPLIED, "SNAPSHOT_APPLIED");
    }

    /** No altera datos/versiones aplicados al guardar el resultado del último intento. */
    public void finish(UUID runId, SynchronizationPhase outcome, String diagnostic) {
        lastRunId = runId.toString();
        phase = outcome;
        safeDiagnostic = diagnostic;
    }

    public Long getAppliedVersion() { return appliedVersion; }
    public String getAppliedRunId() { return appliedRunId; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getLastRunId() { return lastRunId; }
    public SynchronizationPhase getPhase() { return phase; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public String getSafeDiagnostic() { return safeDiagnostic; }
}
