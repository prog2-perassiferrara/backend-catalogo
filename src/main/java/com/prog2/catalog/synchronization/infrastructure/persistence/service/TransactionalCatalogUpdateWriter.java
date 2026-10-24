package com.prog2.catalog.synchronization.infrastructure.persistence.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.*;

/** Frontera transaccional: el adaptador recibe el resultado solo después del commit del proxy. */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 20)
public class TransactionalCatalogUpdateWriter {
    private final JpaProfessionalCategoryRepository categories;
    private final JpaProfessionalRepository professionals;
    private final JpaWeeklyScheduleRepository schedules;
    private final JpaCatalogSyncStateRepository states;
    private final JpaCatalogProcessedEventRepository events;
    private final JpaCatalogRejectedRecordRepository rejections;
    private final CatalogSnapshotPersistenceMapper mapper;
    private final Clock clock;

    public TransactionalCatalogUpdateWriter(JpaProfessionalCategoryRepository categories,
            JpaProfessionalRepository professionals, JpaWeeklyScheduleRepository schedules,
            JpaCatalogSyncStateRepository states, JpaCatalogProcessedEventRepository events,
            JpaCatalogRejectedRecordRepository rejections, CatalogSnapshotPersistenceMapper mapper, Clock clock) {
        this.categories = categories;
        this.professionals = professionals;
        this.schedules = schedules;
        this.states = states;
        this.events = events;
        this.rejections = rejections;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 20)
    public boolean isProcessed(String eventId) {
        return events.findById(digest(eventId)).map(receipt -> {
            if (!receipt.getEventId().equals(eventId)) {
                throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "EVENT_ID_DIGEST_CONFLICT");
            }
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 20)
    public boolean isRejected(RejectedCatalogRecord rejection) {
        return rejections.existsById(key(rejection));
    }

    /** Incremento real: no borra filas ajenas al lote ni reemplaza la colección completa. */
    public CatalogState apply(UUID runId, CatalogChangesBatch batch, CatalogNotification notification) {
        CatalogSyncStateEntity state = locked();
        if (runId.toString().equals(state.getAppliedRunId())) { return mapper.toDomain(state); }
        if (notification != null && isProcessed(notification.eventId())) { return mapper.toDomain(state); }
        requireBase(state, batch.baseVersion());
        batch.validate();
        categories.saveAllAndFlush(batch.categories().stream().map(mapper::toEntity).toList());
        for (Professional value : batch.professionals()) {
            if (!categories.existsById(value.categoryId())) { throw missingReference(); }
        }
        professionals.saveAllAndFlush(batch.professionals().stream().map(mapper::toEntity).toList());
        for (WeeklySchedule value : batch.schedules()) {
            if (!professionals.existsById(value.professionalId())) { throw missingReference(); }
        }
        schedules.saveAllAndFlush(batch.schedules().stream().map(mapper::toEntity).toList());
        state.updated(runId, batch.publication().currentVersion(), clock.instant());
        if (notification != null) { receipt(state, notification); }
        states.saveAndFlush(state);
        return mapper.toDomain(state);
    }

    public void recordCovered(CatalogNotification notification) {
        receipt(locked(), notification);
    }

    public void recordRejection(RejectedCatalogRecord rejection) {
        if (!isRejected(rejection)) {
            rejections.saveAndFlush(new CatalogRejectedRecordEntity(key(rejection), rejection.safeCode(), clock.instant()));
        }
    }

    public CatalogState checked(CatalogPublication publication) {
        CatalogSyncStateEntity state = locked();
        requireBase(state, publication.currentVersion());
        state.checked(publication.currentVersion(), clock.instant());
        return mapper.toDomain(states.saveAndFlush(state));
    }

    public CatalogState report(SynchronizationPhase phase, String safeCode) {
        CatalogSyncStateEntity state = locked();
        state.report(phase, safeCode, clock.instant());
        return mapper.toDomain(states.saveAndFlush(state));
    }

    private void receipt(CatalogSyncStateEntity state, CatalogNotification notification) {
        if (state.getAppliedVersion() == null || state.getAppliedVersion() < notification.newVersion()) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "NOTIFICATION_NOT_COVERED");
        }
        if (!isProcessed(notification.eventId())) {
            events.saveAndFlush(new CatalogProcessedEventEntity(digest(notification.eventId()),
                    notification.eventId(), notification.newVersion(), clock.instant()));
        }
    }

    private CatalogSyncStateEntity locked() { return states.lockState().orElseThrow(); }

    private static void requireBase(CatalogSyncStateEntity state, long version) {
        if (!Objects.equals(state.getAppliedVersion(), version)) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_BASE_CHANGED");
        }
    }

    private static CatalogDiscontinuityException missingReference() {
        return new CatalogDiscontinuityException(CatalogRecoveryReason.INVALID_REFERENCES);
    }

    private static CatalogRejectedRecordKey key(RejectedCatalogRecord value) {
        return new CatalogRejectedRecordKey(value.topic(), value.partition(), value.offset());
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException _) {
            throw new IllegalStateException("SHA256_NOT_AVAILABLE");
        }
    }
}
