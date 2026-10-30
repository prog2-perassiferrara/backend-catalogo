package com.prog2.catalog.integration.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta exclusiva del servicio Turnos autorizado; nunca forma parte de una respuesta a KMP.
 *
 * @param idToken JWT de cátedra conservado por Catálogo
 */
public record TechnicalTokenResponse(@Schema(description = "JWT central, solo para el servicio autorizado.", example = "TOKEN_TECNICO_FICTICIO") String idToken) {

    @Override
    public String toString() {
        return "TechnicalTokenResponse[idToken=REDACTED]";
    }
}
