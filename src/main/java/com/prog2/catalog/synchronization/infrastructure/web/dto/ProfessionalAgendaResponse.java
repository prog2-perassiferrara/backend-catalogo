package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.util.List;

/** Contrato de lectura interna; no serializa modelos de persistencia ni incorpora slots u ocupaciones. */
public record ProfessionalAgendaResponse(ProfessionalResponse professional,
        List<WeeklyScheduleResponse> weeklySchedules, CatalogStatusResponse catalog) {
    /** Datos descriptivos y habilitación del profesional central. */
    public record ProfessionalResponse(long id, long categoryId, String firstName, String lastName, boolean enabled) { }

    /** Horas locales HH:mm:ss, sin conversión a instantes UTC. */
    public record WeeklyScheduleResponse(long id, long professionalId, String dayOfWeek,
            String startTime, String endTime, int slotDurationMinutes, boolean enabled) { }
}
