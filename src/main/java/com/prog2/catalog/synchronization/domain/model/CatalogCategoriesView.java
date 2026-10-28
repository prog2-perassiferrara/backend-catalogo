package com.prog2.catalog.synchronization.domain.model;

import java.util.List;

/** Categorías completas y metadata obtenidas en la misma transacción de lectura. */
public record CatalogCategoriesView(CatalogState catalog, List<ProfessionalCategory> content) {
    public CatalogCategoriesView { content = List.copyOf(content); }
}

