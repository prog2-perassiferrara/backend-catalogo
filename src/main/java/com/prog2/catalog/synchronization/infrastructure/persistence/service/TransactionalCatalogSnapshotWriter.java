package com.prog2.catalog.synchronization.infrastructure.persistence.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.*;

/**
 * Frontera transaccional separada del adaptador. El proxy de Spring confirma antes de devolverle
 * el control, por lo que el adaptador puede traducir también fallos producidos durante el commit.
 */
@Component
public class TransactionalCatalogSnapshotWriter {
    private final JpaProfessionalCategoryRepository categories;
    private final JpaProfessionalRepository professionals;
    private final JpaWeeklyScheduleRepository schedules;
    private final JpaCatalogSyncStateRepository states;
    private final CatalogSnapshotPersistenceMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public TransactionalCatalogSnapshotWriter(JpaProfessionalCategoryRepository categories,
            JpaProfessionalRepository professionals, JpaWeeklyScheduleRepository schedules,
            JpaCatalogSyncStateRepository states, CatalogSnapshotPersistenceMapper mapper,
            EntityManager entityManager, Clock clock) {
        this.categories = categories;
        this.professionals = professionals;
        this.schedules = schedules;
        this.states = states;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Cada reconciliación lee metadata confirmada en un contexto nuevo, sin valores cacheados. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CatalogState getState() {
        return mapper.toDomain(states.findById(1L).orElseThrow());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 20)
    public void beginAttempt(UUID runId, Instant now) {
        lockedState().begin(runId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 20)
    public CatalogState finishAttempt(UUID runId, SynchronizationPhase phase, String diagnostic) {
        CatalogSyncStateEntity state = lockedState();
        // Si el commit terminó mientras esperábamos este bloqueo, conservar su éxito confirmado.
        if (!runId.toString().equals(state.getAppliedRunId())
                && runId.toString().equals(state.getLastRunId())) {
            state.finish(runId, phase, diagnostic);
        }
        return mapper.toDomain(state);
    }

    /**
     * Sustituye tres colecciones y metadata en una transacción nueva. Nunca usar TRUNCATE:
     * su commit implícito rompería la posibilidad de rollback.
     *
     * @param runId identificador que hace idempotente esta aplicación
     * @param snapshot copia completa validada
     * @return metadata que el proxy confirmará antes de entregarla al adaptador
     * @throws CatalogSnapshotException si la versión retrocede
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 20)
    public CatalogState replace(UUID runId, CatalogSnapshot snapshot) {
        snapshot.validate();
        CatalogSyncStateEntity state = lockedState();
        if (runId.toString().equals(state.getAppliedRunId())) {
            return mapper.toDomain(state);
        }
        if (state.getAppliedVersion() != null && snapshot.snapshotVersion() < state.getAppliedVersion()) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "SNAPSHOT_VERSION_REGRESSION");
        }
        schedules.deleteAllInBatch();
        professionals.deleteAllInBatch();
        categories.deleteAllInBatch();
        // Los borrados masivos no actualizan el contexto JPA; no reutilizar entidades previas.
        entityManager.clear();
        categories.saveAllAndFlush(snapshot.professionalCategories().stream().map(mapper::toEntity).toList());
        professionals.saveAllAndFlush(snapshot.professionals().stream().map(mapper::toEntity).toList());
        schedules.saveAllAndFlush(snapshot.weeklySchedules().stream().map(mapper::toEntity).toList());
        state.applied(runId, snapshot.snapshotVersion(), snapshot.generatedAt(), clock.instant());
        return mapper.toDomain(states.saveAndFlush(state));
    }

    private CatalogSyncStateEntity lockedState() {
        return states.lockState().orElseThrow();
    }
}
