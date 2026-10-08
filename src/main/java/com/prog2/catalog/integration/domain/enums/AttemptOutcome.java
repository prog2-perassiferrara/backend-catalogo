package com.prog2.catalog.integration.domain.enums;

/** Resultado de un intento, utilizado por el coordinador para continuar o detener el ciclo. */
public enum AttemptOutcome {
    /** Acceso técnico utilizable; el ciclo termina con éxito. */
    AVAILABLE,
    /** Fallo temporal o aprovisionamiento pendiente; se puede repetir si quedan intentos. */
    RETRYABLE,
    /** Fallo que detiene el ciclo y requiere recuperación tras reiniciar. */
    TERMINAL
}
