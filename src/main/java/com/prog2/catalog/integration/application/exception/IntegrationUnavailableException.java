package com.prog2.catalog.integration.application.exception;

/** Señala que el caso de uso no puede entregar un JWT técnico utilizable, sin divulgar secretos. */
public class IntegrationUnavailableException extends RuntimeException {
    public IntegrationUnavailableException() {
        super("Technical integration is unavailable");
    }
}
