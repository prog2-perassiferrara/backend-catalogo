package com.prog2.catalog.synchronization.domain.enums;

/** Estado de la última carga; una copia aplicada no garantiza vigencia continua. */
public enum SynchronizationPhase {
    NOT_STARTED,
    IN_PROGRESS,
    /** Copia consistente en una versión concreta, sin afirmar vigencia continua. */
    SNAPSHOT_APPLIED,
    FAILED,
    /** No fue posible verificar el resultado o registrar su diagnóstico. */
    UNVERIFIABLE,
    /** Otra invocación ya ejecuta el ciclo; este resultado no se persiste como último intento. */
    BUSY
}
