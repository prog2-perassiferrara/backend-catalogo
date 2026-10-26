package com.prog2.catalog.synchronization.domain.model;

/** Identifica un rechazo sin guardar ni mostrar el cuerpo recibido. */
public record RejectedCatalogRecord(String topic, int partition, long offset, String safeCode) {
    public RejectedCatalogRecord {
        if (topic == null || topic.isBlank() || topic.length() > 249 || partition < 0 || offset < 0
                || !"INCOMPATIBLE_CATALOG_NOTIFICATION".equals(safeCode)) {
            throw new IllegalArgumentException("INVALID_REJECTION_COORDINATES");
        }
    }
}
