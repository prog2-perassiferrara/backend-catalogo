package com.prog2.catalog.synchronization.infrastructure.client.dto;

/** Representación JSON del profesional central. */
public record ProfessionalResponse(Long id, Long categoryId, String firstName, String lastName,
        Boolean enabled, String createdAt, String updatedAt) { }
