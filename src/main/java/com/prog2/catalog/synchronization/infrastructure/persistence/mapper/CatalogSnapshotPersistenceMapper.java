package com.prog2.catalog.synchronization.infrastructure.persistence.mapper;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.*;

/** Mapeo manual entre valores de dominio y filas. No convierte horarios locales a instantes. */
@Component
public class CatalogSnapshotPersistenceMapper {
    public ProfessionalCategoryEntity toEntity(ProfessionalCategory value) {
        return new ProfessionalCategoryEntity(value.id(), value.name(), value.description(), value.enabled(),
                value.createdAt(), value.updatedAt());
    }

    public ProfessionalEntity toEntity(Professional value) {
        return new ProfessionalEntity(value.id(), value.categoryId(), value.firstName(), value.lastName(),
                value.enabled(), value.createdAt(), value.updatedAt());
    }

    public WeeklyScheduleEntity toEntity(WeeklySchedule value) {
        return new WeeklyScheduleEntity(value.id(), value.professionalId(), value.dayOfWeek(),
                value.startTime(), value.endTime(), value.slotDurationMinutes(), value.enabled(),
                value.createdAt(), value.updatedAt());
    }

    public CatalogState toDomain(CatalogSyncStateEntity value) {
        return new CatalogState(value.getAppliedVersion(), uuid(value.getAppliedRunId()),
                value.getGeneratedAt(), value.getAppliedAt(), uuid(value.getLastRunId()),
                value.getPhase(), value.getAttemptedAt(), value.getSafeDiagnostic());
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
