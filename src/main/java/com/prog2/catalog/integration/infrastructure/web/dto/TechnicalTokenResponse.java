package com.prog2.catalog.integration.infrastructure.web.dto;

/**
 * Respuesta exclusiva del servicio Turnos autorizado; nunca forma parte de una respuesta a KMP.
 *
 * @param idToken JWT de cátedra conservado por Catálogo
 */
public record TechnicalTokenResponse(String idToken) {

    @Override
    public String toString() {
        return "TechnicalTokenResponse[idToken=REDACTED]";
    }
}
