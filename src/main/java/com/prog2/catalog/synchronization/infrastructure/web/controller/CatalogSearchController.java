package com.prog2.catalog.synchronization.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.PROBLEM_SCHEMA;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.UNAUTHORIZED_RESPONSE;
import static com.prog2.catalog.infrastructure.config.OpenApiConfiguration.FORBIDDEN_RESPONSE;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.prog2.catalog.synchronization.domain.ports.in.GetProfessionalCategoriesUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.SearchProfessionalsUseCase;
import com.prog2.catalog.synchronization.infrastructure.web.dto.*;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogSearchDtoMapper;

/** Adaptador HTTP de CU-04; la seguridad existente exige identidad de usuario con ROLE_USER. */
@RestController
public class CatalogSearchController {
    private final SearchProfessionalsUseCase search;
    private final GetProfessionalCategoriesUseCase categories;
    private final CatalogSearchDtoMapper mapper;

    public CatalogSearchController(SearchProfessionalsUseCase search, GetProfessionalCategoriesUseCase categories,
            CatalogSearchDtoMapper mapper) {
        this.search = search;
        this.categories = categories;
        this.mapper = mapper;
    }

    /** @return coincidencias paginadas, totales y estado del mismo recorrido de lectura */
    @Operation(summary = "Buscar profesionales", description = "JWT de usuario con ROLE_USER. Filtros opcionales combinados; disponibilidad significa agenda semanal habilitada, sin garantizar huecos libres.", tags = {"Catálogo"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE),
        @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE),
        @ApiResponse(responseCode = "400", description = "INVALID_SEARCH_CRITERIA",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(ref = PROBLEM_SCHEMA))),
        @ApiResponse(responseCode = "503", description = "CATALOG_COPY_UNAVAILABLE o CATALOG_UNAVAILABLE",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(ref = PROBLEM_SCHEMA)))
    })
    @GetMapping(path = "/api/catalog/professionals", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalSearchResponse> search(@Valid @ParameterObject @ModelAttribute ProfessionalSearchRequest request) {
        return noCache(mapper.toResponse(search.search(mapper.toCriteria(request))));
    }

    /** @return todas las categorías, incluidas deshabilitadas, con estado apropiado */
    @Operation(summary = "Consultar categorías", description = "JWT de usuario con ROLE_USER. Incluye categorías deshabilitadas y el estado de la copia consultada.", tags = {"Catálogo"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta exitosa.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", ref = UNAUTHORIZED_RESPONSE),
        @ApiResponse(responseCode = "403", ref = FORBIDDEN_RESPONSE),
        @ApiResponse(responseCode = "503", description = "CATALOG_COPY_UNAVAILABLE o CATALOG_UNAVAILABLE",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(ref = PROBLEM_SCHEMA)))
    })
    @GetMapping(path = "/api/catalog/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalCategoriesResponse> categories() {
        return noCache(mapper.toResponse(categories.get()));
    }

    private static <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}

