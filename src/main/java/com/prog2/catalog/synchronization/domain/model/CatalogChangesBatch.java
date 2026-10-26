package com.prog2.catalog.synchronization.domain.model;

import java.util.List;
import com.prog2.catalog.synchronization.domain.enums.CatalogRecoveryReason;
import com.prog2.catalog.synchronization.domain.exception.CatalogDiscontinuityException;

/** Rango continuo: los IDs se unen y sus valores corresponden a la publicación objetivo. */
public record CatalogChangesBatch(long baseVersion, CatalogPublication publication,
        List<ProfessionalCategory> categories, List<Professional> professionals, List<WeeklySchedule> schedules) {
    public CatalogChangesBatch {
        categories = List.copyOf(categories);
        professionals = List.copyOf(professionals);
        schedules = List.copyOf(schedules);
    }

    /** Valida campos y unicidad; la persistencia comprobará referencias contra la copia existente. */
    public void validate() {
        try {
            boolean valid = publication != null && publication.publishedAt() != null
                    && publication.currentVersion() >= baseVersion
                    && categories.stream().map(ProfessionalCategory::id).distinct().count() == categories.size()
                    && professionals.stream().map(Professional::id).distinct().count() == professionals.size()
                    && schedules.stream().map(WeeklySchedule::id).distinct().count() == schedules.size()
                    && categories.stream().allMatch(v -> v.name() != null && v.createdAt() != null && v.updatedAt() != null)
                    && professionals.stream().allMatch(v -> v.firstName() != null && v.lastName() != null
                            && v.createdAt() != null && v.updatedAt() != null)
                    && schedules.stream().allMatch(v -> v.dayOfWeek() != null && v.startTime() != null
                            && v.endTime() != null && v.createdAt() != null && v.updatedAt() != null);
            if (!valid) { throw invalid(); }
        } catch (NullPointerException _) {
            throw invalid();
        }
    }

    private static CatalogDiscontinuityException invalid() {
        return new CatalogDiscontinuityException(CatalogRecoveryReason.INVALID_REMOTE_DATA);
    }
}
