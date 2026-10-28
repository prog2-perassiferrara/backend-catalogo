package com.prog2.catalog.synchronization.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.web.dto.*;

/** Adapta los filtros y publica solo los campos de CU-04, reutilizando el resumen de estado. */
@Component
public class CatalogSearchDtoMapper {
    private final CatalogQueryDtoMapper statuses;

    public CatalogSearchDtoMapper(CatalogQueryDtoMapper statuses) { this.statuses = statuses; }

    /** @return criterios normalizados con los defaults aprobados cuando se omite paginación */
    public ProfessionalSearchCriteria toCriteria(ProfessionalSearchRequest request) {
        return new ProfessionalSearchCriteria(request.categoryId(), request.name(), request.enabled(), request.availableOn(),
                request.page() == null ? ProfessionalSearchCriteria.DEFAULT_PAGE : request.page(),
                request.size() == null ? ProfessionalSearchCriteria.DEFAULT_SIZE : request.size());
    }

    /** @return página completa y resumen seguro del estado de esa consulta */
    public ProfessionalSearchResponse toResponse(ProfessionalSearchResult result) {
        var content = result.content().stream().map(this::professional).toList();
        return new ProfessionalSearchResponse(content, result.page(), result.size(), result.totalElements(),
                result.totalPages(), statuses.toSummary(result.catalog()));
    }

    /** @return categorías con flags originales, sin tiempos o metadata internos */
    public ProfessionalCategoriesResponse toResponse(ProfessionalCategoriesResult result) {
        return new ProfessionalCategoriesResponse(result.content().stream().map(this::category).toList(),
                statuses.toSummary(result.catalog()));
    }

    private ProfessionalSearchResponse.ProfessionalResponse professional(ProfessionalSearchEntry entry) {
        Professional value = entry.professional();
        return new ProfessionalSearchResponse.ProfessionalResponse(value.id(), value.firstName(), value.lastName(),
                value.enabled(), category(entry.category()));
    }

    private CatalogCategoryResponse category(ProfessionalCategory value) {
        return new CatalogCategoryResponse(value.id(), value.name(), value.enabled());
    }
}

