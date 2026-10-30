package com.prog2.catalog.synchronization.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.prog2.catalog.synchronization.domain.enums.CatalogAvailability;

/** Resumen permitido a KMP: distingue copia existente de vigencia sin exponer metadata técnica. */
public record CatalogStatusSummaryResponse(@Schema(implementation = CatalogAvailability.class) String state, boolean copyAvailable) { }
