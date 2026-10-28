package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.util.List;

/** Opciones completas de categorías con estado del catálogo consultado. */
public record ProfessionalCategoriesResponse(List<CatalogCategoryResponse> content, CatalogStatusSummaryResponse catalog) { }

