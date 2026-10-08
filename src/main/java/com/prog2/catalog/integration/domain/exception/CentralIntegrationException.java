package com.prog2.catalog.integration.domain.exception;

/** Fallo del puerto de cátedra, clasificado sin exponer tipos HTTP ni respuestas externas. */
public class CentralIntegrationException extends RuntimeException {

    private final boolean retryable;

    /**
     * @param retryable si el ciclo puede repetir el intento dentro del límite acordado
     * @param safeDiagnostic diagnóstico sin secretos ni texto libre recibido de cátedra
     */
    public CentralIntegrationException(boolean retryable, String safeDiagnostic) {
        // No conservar causas HTTP que puedan incluir cuerpos o secretos.
        super(safeDiagnostic);
        this.retryable = retryable;
    }

    /** @return si el fallo permite continuar el ciclo limitado */
    public boolean isRetryable() {
        return retryable;
    }
}
