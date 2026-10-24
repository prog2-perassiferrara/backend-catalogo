package com.prog2.catalog.synchronization.domain.enums;

/** Motivos seguros y tipados para abandonar una secuencia incremental. */
public enum CatalogRecoveryReason {
    HISTORY_UNAVAILABLE, CHANGE_MISSING, INVALID_REMOTE_DATA, PUBLICATION_CHANGED,
    READ_LIMIT_EXCEEDED, INVALID_REFERENCES
}
