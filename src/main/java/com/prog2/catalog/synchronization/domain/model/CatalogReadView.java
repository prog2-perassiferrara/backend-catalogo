package com.prog2.catalog.synchronization.domain.model;

import java.util.List;
import java.util.Optional;

/** Una misma vista confirmada de metadata y datos; ausencia de profesional se evalúa después de la vigencia. */
public record CatalogReadView(CatalogState catalog, Optional<Professional> professional,
        List<WeeklySchedule> weeklySchedules) {
    public CatalogReadView {
        weeklySchedules = List.copyOf(weeklySchedules);
    }
}
