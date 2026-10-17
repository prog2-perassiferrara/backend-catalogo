package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;

/** Profesional central, relacionado con su categoría mediante el ID externo. */
public record Professional(long id, long categoryId, String firstName, String lastName,
        boolean enabled, Instant createdAt, Instant updatedAt) { }
