package com.prog2.catalog.synchronization.domain.enums;

/** Clasificación del fallo que determina si se detiene, reintenta o reconcilia la carga. */
public enum SnapshotFailureKind {
    /** Fallo recuperable; no hay una escritura pendiente de verificar. */
    TEMPORARY,
    /** Repetir no resuelve el defecto o la respuesta inválida. */
    TERMINAL,
    /** Token técnico rechazado o no utilizable; no equivale al JWT de un usuario. */
    UNAUTHORIZED,
    /** Se perdió confirmación de la operación local; consultar metadata antes de repetir. */
    UNCERTAIN
}
