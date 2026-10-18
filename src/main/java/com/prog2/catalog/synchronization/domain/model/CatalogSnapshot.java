package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;

/** Copia completa inmutable. La validación conjunta evita aplicar referencias o listas parciales. */
public record CatalogSnapshot(long snapshotVersion, Instant generatedAt,
        List<ProfessionalCategory> professionalCategories, List<Professional> professionals,
        List<WeeklySchedule> weeklySchedules) {

    public CatalogSnapshot {
        try {
            professionalCategories = List.copyOf(professionalCategories);
            professionals = List.copyOf(professionals);
            weeklySchedules = List.copyOf(weeklySchedules);
        } catch (NullPointerException _) {
            throw invalid();
        }
    }

    /**
     * Comprueba presencia, unicidad y referencias dentro de esta misma copia.
     * No filtra deshabilitados ni impone reglas de agenda no definidas por el contrato.
     *
     * @throws CatalogSnapshotException si la copia no puede aplicarse completa
     */
    public void validate() {
        require(generatedAt != null);
        Set<Long> categoryIds = new HashSet<>();
        for (ProfessionalCategory category : professionalCategories) {
            require(categoryIds.add(category.id()) && category.name() != null
                    && category.createdAt() != null && category.updatedAt() != null);
        }
        Set<Long> professionalIds = new HashSet<>();
        for (Professional professional : professionals) {
            require(professionalIds.add(professional.id()) && categoryIds.contains(professional.categoryId())
                    && professional.firstName() != null && professional.lastName() != null
                    && professional.createdAt() != null && professional.updatedAt() != null);
        }
        Set<Long> scheduleIds = new HashSet<>();
        for (WeeklySchedule schedule : weeklySchedules) {
            require(scheduleIds.add(schedule.id()) && professionalIds.contains(schedule.professionalId())
                    && schedule.dayOfWeek() != null && schedule.startTime() != null
                    && schedule.endTime() != null && schedule.createdAt() != null && schedule.updatedAt() != null);
        }
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw invalid();
        }
    }

    private static CatalogSnapshotException invalid() {
        return new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "INVALID_CATALOG_SNAPSHOT");
    }
}
