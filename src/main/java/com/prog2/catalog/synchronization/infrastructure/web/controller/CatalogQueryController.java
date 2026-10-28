package com.prog2.catalog.synchronization.infrastructure.web.controller;

import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.infrastructure.web.dto.*;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogQueryDtoMapper;

/** Adaptador HTTP de CU-05/CU-06; filtros previos separan identidades y permisos de cada ruta. */
@RestController
public class CatalogQueryController {
    private final GetProfessionalAgendaUseCase agendas;
    private final GetCatalogStatusUseCase statuses;
    private final CatalogQueryDtoMapper mapper;

    public CatalogQueryController(GetProfessionalAgendaUseCase agendas, GetCatalogStatusUseCase statuses,
            CatalogQueryDtoMapper mapper) {
        this.agendas = agendas;
        this.statuses = statuses;
        this.mapper = mapper;
    }

    /** @return profesional y todos sus horarios, únicamente con vigencia acreditada en esta lectura */
    @GetMapping(path = "/api/internal/catalog/professionals/{professionalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalAgendaResponse> getProfessionalAgenda(@PathVariable("professionalId") @Positive long professionalId) {
        return noCache(mapper.toResponse(agendas.get(professionalId)));
    }

    /** @return estado funcional detallado, incluso sin copia o con sincronización fallida */
    @GetMapping(path = "/api/internal/catalog/synchronization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogStatusResponse> getServiceStatus() {
        return noCache(mapper.toResponse(statuses.get()));
    }

    /** @return resumen para usuarios autorizados, sin credenciales ni diagnósticos técnicos */
    @GetMapping(path = "/api/catalog/synchronization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogStatusSummaryResponse> getUserStatus() {
        return noCache(mapper.toSummary(statuses.get()));
    }

    private static <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
