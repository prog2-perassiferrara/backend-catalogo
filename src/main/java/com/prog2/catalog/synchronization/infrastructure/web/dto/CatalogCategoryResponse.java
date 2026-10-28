package com.prog2.catalog.synchronization.infrastructure.web.dto;

/** Campos públicos de la categoría para resultados y opciones del filtro. */
public record CatalogCategoryResponse(long id, String name, boolean enabled) { }

