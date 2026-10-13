package com.prog2.catalog.integration.domain.enums;

/** Estado local de la sesión técnica; representa la evaluación realizada por Catálogo. */
public enum IntegrationState {
    /** Acceso todavía no habilitado, incluso si ya se obtuvo un JWT con aprovisionamiento pendiente. */
    INITIALIZING,
    /** Sesión habilitada, cuyo vencimiento conocido se comprueba al consultar el acceso. */
    READY,
    /** Acceso descartado; reiniciar comienza una sesión y un ciclo nuevos. */
    FAILED
}
