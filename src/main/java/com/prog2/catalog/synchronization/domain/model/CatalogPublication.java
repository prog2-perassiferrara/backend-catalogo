package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;

/** Metadata observada de una publicación central. No contiene estado histórico de entidades. */
public record CatalogPublication(long currentVersion, long oldestAvailableVersion, Instant publishedAt) { }
