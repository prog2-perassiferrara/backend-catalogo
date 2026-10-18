package com.prog2.catalog.synchronization.domain.exception;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;

/** Fallo del contrato de snapshot. Conserva solo un diagnóstico interno, nunca causas externas. */
public class CatalogSnapshotException extends RuntimeException {
    private final SnapshotFailureKind kind;

    public CatalogSnapshotException(SnapshotFailureKind kind, String safeDiagnostic) {
        super(safeDiagnostic);
        this.kind = kind;
    }

    public SnapshotFailureKind kind() {
        return kind;
    }
}
