package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.time.Instant;

/** Proyección de CU-06 para Turnos y comprobación incluida en CU-05; no contiene diagnósticos privados. */
public record CatalogStatusResponse(String state, boolean copyAvailable, Long appliedVersion,
        Long observedVersion, Instant lastCheckedAt, Instant evaluatedAt) { }
