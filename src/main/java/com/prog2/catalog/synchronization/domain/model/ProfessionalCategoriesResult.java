package com.prog2.catalog.synchronization.domain.model;

import java.util.List;

/** Opciones de categoría, incluidas deshabilitadas, con estado funcional de su consulta. */
public record ProfessionalCategoriesResult(List<ProfessionalCategory> content, CatalogStatus catalog) {
    public ProfessionalCategoriesResult { content = List.copyOf(content); }
}

