package com.prog2.catalog.synchronization.domain.model;

import java.util.List;

/** Página materializada y estado evaluado al resolver la consulta del usuario. */
public record ProfessionalSearchResult(List<ProfessionalSearchEntry> content, int page, int size,
        long totalElements, CatalogStatus catalog) {
    public ProfessionalSearchResult { content = List.copyOf(content); }

    /** @return cantidad de páginas, cero para un catálogo sin coincidencias */
    public long totalPages() { return Math.ceilDiv(totalElements, (long) size); }
}

