package com.prog2.catalog.synchronization.application.exception;

import com.prog2.catalog.synchronization.domain.enums.CatalogQueryFailure;

/** Rechazo tipado de una consulta; no contiene entidades ni detalles de transporte. */
public class CatalogQueryException extends RuntimeException {
    private final CatalogQueryFailure reason;

    public CatalogQueryException(CatalogQueryFailure reason) {
        super(reason.name());
        this.reason = reason;
    }

    /** @return motivo estable que el adaptador HTTP traduce al estado correspondiente */
    public CatalogQueryFailure reason() { return reason; }
}
