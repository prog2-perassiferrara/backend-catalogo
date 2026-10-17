package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;

/** Categoría central inmutable; conserva el ID y la habilitación originales. */
public record ProfessionalCategory(long id, String name, String description, boolean enabled,
        Instant createdAt, Instant updatedAt) { }
