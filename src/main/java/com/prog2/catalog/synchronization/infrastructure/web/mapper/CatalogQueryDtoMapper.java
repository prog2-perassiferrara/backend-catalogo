package com.prog2.catalog.synchronization.infrastructure.web.mapper;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.web.dto.*;
import com.prog2.catalog.synchronization.infrastructure.web.dto.ProfessionalAgendaResponse.*;

/** Proyecta únicamente campos del contrato HTTP; la vigencia ya fue evaluada por la aplicación. */
@Component
public class CatalogQueryDtoMapper {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** @return datos y comprobación conjuntamente, sin campos privados de sincronización */
    public ProfessionalAgendaResponse toResponse(ProfessionalAgenda agenda) {
        Professional value = agenda.professional();
        var professional = new ProfessionalResponse(value.id(), value.categoryId(), value.firstName(), value.lastName(), value.enabled());
        return new ProfessionalAgendaResponse(professional,
                agenda.weeklySchedules().stream().map(this::schedule).toList(), toResponse(agenda.catalog()));
    }

    /** @return detalle de versión/comprobación para la identidad de servicio */
    public CatalogStatusResponse toResponse(CatalogStatus status) {
        return new CatalogStatusResponse(status.state().name(), status.copyAvailable(), status.appliedVersion(),
                status.observedVersion(), status.lastCheckedAt(), status.evaluatedAt());
    }

    /** @return resumen sin versiones, diagnósticos, identificadores de ejecución ni secretos */
    public CatalogStatusSummaryResponse toSummary(CatalogStatus status) {
        return new CatalogStatusSummaryResponse(status.state().name(), status.copyAvailable());
    }

    private WeeklyScheduleResponse schedule(WeeklySchedule value) {
        return new WeeklyScheduleResponse(value.id(), value.professionalId(), value.dayOfWeek().name(),
                TIME.format(value.startTime()), TIME.format(value.endTime()), value.slotDurationMinutes(), value.enabled());
    }
}
