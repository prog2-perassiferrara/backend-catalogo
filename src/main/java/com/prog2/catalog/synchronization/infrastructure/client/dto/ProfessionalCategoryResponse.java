package com.prog2.catalog.synchronization.infrastructure.client.dto;

/** Representación JSON de categoría, independiente del dominio y de JPA. */
public record ProfessionalCategoryResponse(Long id, String name, String description, Boolean enabled,
        String createdAt, String updatedAt) { }
