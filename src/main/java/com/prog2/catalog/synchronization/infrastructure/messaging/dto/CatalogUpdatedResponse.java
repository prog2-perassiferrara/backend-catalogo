package com.prog2.catalog.synchronization.infrastructure.messaging.dto;

/** Envoltura externa v1. Los campos obligatorios usan wrappers para detectar ausencias. */
public record CatalogUpdatedResponse(String eventId, String eventType, Long newVersion,
        String occurredAt, String source, Integer schemaVersion) { }
