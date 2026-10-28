package com.prog2.catalog.synchronization.domain.model;

import java.util.List;

/** Resultado conjunto de CU-05, con horarios completos y vigencia acreditada al resolver la consulta. */
public record ProfessionalAgenda(Professional professional, List<WeeklySchedule> weeklySchedules,
        CatalogStatus catalog) {
    public ProfessionalAgenda {
        weeklySchedules = List.copyOf(weeklySchedules);
    }
}
