package com.prog2.catalog.synchronization.domain.enums;

/** Motivos públicos de rechazo; HTTP se decide exclusivamente en infraestructura. */
public enum CatalogQueryFailure {
    INVALID_PROFESSIONAL_ID, PROFESSIONAL_NOT_FOUND, CATALOG_NOT_CURRENT, CATALOG_UNAVAILABLE,
    INVALID_SEARCH_CRITERIA, CATALOG_COPY_UNAVAILABLE
}
