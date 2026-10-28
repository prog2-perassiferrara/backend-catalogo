package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;
import com.prog2.catalog.synchronization.domain.enums.CatalogAvailability;

/** Comprobación fechada del catálogo; no autoriza futuras reservas ni revela diagnósticos internos. */
public record CatalogStatus(CatalogAvailability state, boolean copyAvailable, Long appliedVersion,
        Long observedVersion, Instant lastCheckedAt, Instant evaluatedAt) { }
