package com.prog2.catalog.synchronization.domain.model;

import java.time.Instant;

/** Notificación funcional; la identidad no se normaliza ni se sustituye por la versión. */
public record CatalogNotification(String eventId, long newVersion, Instant occurredAt, String source) {
    public CatalogNotification {
        if (eventId == null || eventId.isBlank() || occurredAt == null || source == null || source.isBlank()) {
            throw new IllegalArgumentException("INVALID_CATALOG_NOTIFICATION");
        }
    }
}
