package com.prog2.catalog.integration.infrastructure.client.dto;

/**
 * Cuerpo del login técnico de cátedra; su representación de texto oculta las credenciales.
 *
 * @param username usuario técnico previamente registrado
 * @param password contraseña secreta de la cuenta
 * @param rememberMe solicitud de acceso de larga duración, enviada como {@code true}
 */
public record LoginRequest(String username, String password, boolean rememberMe) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
