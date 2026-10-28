package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.util.List;

/** Contrato paginado de KMP con estado asociado, sin metadata interna ni datos de integración. */
public record ProfessionalSearchResponse(List<ProfessionalResponse> content, int page, int size,
        long totalElements, long totalPages, CatalogStatusSummaryResponse catalog) {
    /** Profesional resumido; la habilitación de su categoría permanece distinguible. */
    public record ProfessionalResponse(long id, String firstName, String lastName,
            boolean enabled, CatalogCategoryResponse category) { }
}

