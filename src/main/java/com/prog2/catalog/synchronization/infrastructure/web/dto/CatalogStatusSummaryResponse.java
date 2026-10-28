package com.prog2.catalog.synchronization.infrastructure.web.dto;

/** Resumen permitido a KMP: distingue copia existente de vigencia sin exponer metadata técnica. */
public record CatalogStatusSummaryResponse(String state, boolean copyAvailable) { }
