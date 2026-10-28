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
        return new WeeklyScheduleEntity(value);
    }

    /** Materializa el profesional antes de cerrar la transacción de lectura. */
    public Professional toDomain(ProfessionalEntity value) {
        return new Professional(value.getId(), value.getCategoryId(), value.getFirstName(), value.getLastName(),
                value.getEnabled(), value.getCreatedAt(), value.getUpdatedAt());
    }

    /** Conserva horas locales y habilitación, sin calcular disponibilidad. */
    public WeeklySchedule toDomain(WeeklyScheduleEntity value) {
        return new WeeklySchedule(value.getId(), value.getProfessionalId(), value.getDayOfWeek(),
                value.getStartTime(), value.getEndTime(), value.getSlotDurationMinutes(), value.getEnabled(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    public CatalogState toDomain(CatalogSyncStateEntity value) {
        return new CatalogState(value.getAppliedVersion(), uuid(value.getAppliedRunId()),
                value.getGeneratedAt(), value.getAppliedAt(), uuid(value.getLastRunId()),
                value.getPhase(), value.getAttemptedAt(), value.getSafeDiagnostic(),
                value.getObservedVersion(), value.getLastCheckedAt());
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
