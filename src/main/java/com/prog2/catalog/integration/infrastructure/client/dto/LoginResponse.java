package com.prog2.catalog.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del login: JWT en {@code id_token} y configuración dentro de {@code integration}.
 * Acepta campos adicionales compatibles y oculta el contenido en su representación de texto.
 *
 * @param token JWT técnico secreto devuelto por cátedra
 * @param integration configuración y estado de aprovisionamiento recibidos junto al JWT
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(@JsonProperty("id_token") String token, IntegrationResponse integration) {
    @Override
    public String toString() {
        return "LoginResponse[redacted]";
    }
}
