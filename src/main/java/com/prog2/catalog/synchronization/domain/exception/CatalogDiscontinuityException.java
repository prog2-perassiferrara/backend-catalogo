package com.prog2.catalog.synchronization.domain.exception;

import com.prog2.catalog.synchronization.domain.enums.CatalogRecoveryReason;

/** Datos remotos no verificables: requieren snapshot, no continuar saltando cambios. */
public class CatalogDiscontinuityException extends RuntimeException {
    private final CatalogRecoveryReason reason;
    public CatalogDiscontinuityException(CatalogRecoveryReason reason) {
        super(reason.name());
        this.reason = reason;
    }
    public CatalogRecoveryReason reason() { return reason; }
}
