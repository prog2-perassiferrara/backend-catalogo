package com.prog2.catalog.synchronization.domain.enums;

/** Estado funcional derivado para consumidores; no reemplaza la fase persistida de sincronización. */
public enum CatalogAvailability {
    NO_COPY, CURRENT, SYNCHRONIZING, RECOVERING, STALE, FAILED, UNVERIFIABLE
}
