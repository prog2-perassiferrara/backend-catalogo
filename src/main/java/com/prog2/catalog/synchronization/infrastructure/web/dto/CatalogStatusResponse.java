package com.prog2.catalog.synchronization.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.prog2.catalog.synchronization.domain.enums.CatalogAvailability;

import java.time.Instant;

/** Proyección de CU-06 para Turnos y comprobación incluida en CU-05; no contiene diagnósticos privados. */
public record CatalogStatusResponse(@Schema(implementation = CatalogAvailability.class) String state,
        boolean copyAvailable,
        @Schema(types = {"integer", "null"}, format = "int64") Long appliedVersion,
        @Schema(types = {"integer", "null"}, format = "int64") Long observedVersion,
        @Schema(types = {"string", "null"}, format = "date-time") Instant lastCheckedAt,
        Instant evaluatedAt) { }
