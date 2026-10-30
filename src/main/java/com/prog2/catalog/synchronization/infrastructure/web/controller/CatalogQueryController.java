package com.prog2.catalog.synchronization.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.PROBLEM_SCHEMA;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.UNAUTHORIZED_RESPONSE;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.FORBIDDEN_RESPONSE;

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
    @Operation(summary = "Consultar profesional y agenda vigentes", description = "JWT de servicio Turnos con ROLE_CATALOG_INTEGRATION. Requiere catálogo vigente; devuelve todos los horarios semanales, sin ocupaciones ni slots libres.", tags = {"Integración interna"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "400", description = "INVALID_PROFESSIONAL_ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "404", description = "PROFESSIONAL_NOT_FOUND",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @ApiResponse(responseCode = "503", description = "CATALOG_NOT_CURRENT o CATALOG_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(path = "/api/internal/catalog/professionals/{professionalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalAgendaResponse> getProfessionalAgenda(@PathVariable("professionalId") @Positive long professionalId) {
        return noCache(mapper.toResponse(agendas.get(professionalId)));
    }

    /** @return estado funcional detallado, incluso sin copia o con sincronización fallida */
    @Operation(summary = "Consultar estado detallado", description = "JWT de servicio Turnos con ROLE_CATALOG_INTEGRATION. Incluye versiones y fechas, también sin copia o ante fallos de sincronización.", tags = {"Integración interna"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "503", description = "CATALOG_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(path = "/api/internal/catalog/synchronization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogStatusResponse> getServiceStatus() {
        return noCache(mapper.toResponse(statuses.get()));
    }

    /** @return resumen para usuarios autorizados, sin credenciales ni diagnósticos técnicos */
    @Operation(summary = "Consultar estado resumido", description = "JWT de usuario con ROLE_USER. Tener una copia no acredita vigencia; las búsquedas pueden utilizarla con aviso.", tags = {"Catálogo"})
    @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE)
    @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE)
    @ApiResponse(responseCode = "503", description = "CATALOG_UNAVAILABLE",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(ref = PROBLEM_SCHEMA)))
    @GetMapping(path = "/api/catalog/synchronization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogStatusSummaryResponse> getUserStatus() {
        return noCache(mapper.toSummary(statuses.get()));
    }

    private static <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
