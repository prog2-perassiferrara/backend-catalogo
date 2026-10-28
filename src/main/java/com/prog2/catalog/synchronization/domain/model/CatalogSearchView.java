package com.prog2.catalog.synchronization.domain.model;

import java.util.List;

/** Página, total y metadata leídos en una única vista confirmada de persistencia. */
public record CatalogSearchView(CatalogState catalog, List<ProfessionalSearchEntry> content, long totalElements) {
    public CatalogSearchView { content = List.copyOf(content); }
}

