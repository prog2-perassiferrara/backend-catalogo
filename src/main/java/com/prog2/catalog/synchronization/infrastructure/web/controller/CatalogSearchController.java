package com.prog2.catalog.synchronization.infrastructure.web.controller;

import jakarta.validation.Valid;
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
    @GetMapping(path = "/api/catalog/professionals", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalSearchResponse> search(@Valid @ModelAttribute ProfessionalSearchRequest request) {
        return noCache(mapper.toResponse(search.search(mapper.toCriteria(request))));
    }

    /** @return todas las categorías, incluidas deshabilitadas, con estado apropiado */
    @GetMapping(path = "/api/catalog/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfessionalCategoriesResponse> categories() {
        return noCache(mapper.toResponse(categories.get()));
    }

    private static <T> ResponseEntity<T> noCache(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}

