package com.prog2.catalog.integration.infrastructure.config;

import java.net.URI;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;

/**
 * Parámetros locales suministrados para autenticar la cuenta técnica registrada manualmente.
 * Spring los obtiene del entorno del proceso; Compose se encarga de suministrar los valores de .env.
 * La representación de texto oculta los valores para evitar divulgar credenciales.
 *
 * @param baseUrl URL base HTTP/HTTPS de cátedra
 * @param username usuario de la cuenta técnica
 * @param password contraseña secreta de la cuenta técnica
 * @param groupId identificador normalizado esperado en las respuestas de cátedra
 */
public record IntegrationProperties(URI baseUrl, String username, String password, String groupId) {

    /**
     * Lee las cuatro variables {@code CATEDRA_*} obligatorias y valida su formato contractual.
     * Los diagnósticos identifican la variable inválida sin incorporar su valor.
     *
     * @param environment fuentes de configuración del proceso administradas por Spring
     * @return propiedades validadas
     * @throws BeanCreationException si falta un valor obligatorio o su formato es inválido
     */
    public static IntegrationProperties from(Environment environment) {
        String baseUrl = required(environment, "CATEDRA_BASE_URL");
        String username = required(environment, "CATEDRA_USERNAME");
        String password = required(environment, "CATEDRA_PASSWORD");
        String groupId = required(environment, "CATEDRA_GROUP_ID");
        if (username.length() > 254) {
            throw invalid("CATEDRA_USERNAME");
        }
        if (password.length() < 4 || password.length() > 100) {
            throw invalid("CATEDRA_PASSWORD");
        }
        if (groupId.length() < 3 || groupId.length() > 100
                || !groupId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw invalid("CATEDRA_GROUP_ID");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException ignored) {
            throw invalid("CATEDRA_BASE_URL");
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPort() > 65535 || uri.getPort() == 0) {
            throw invalid("CATEDRA_BASE_URL");
        }
        return new IntegrationProperties(uri, username, password, groupId);
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new BeanCreationException("integrationConfiguration",
                    "Required integration setting is missing or blank: " + name);
        }
        return value;
    }

    private static BeanCreationException invalid(String name) {
        return new BeanCreationException("integrationConfiguration", "Invalid integration setting: " + name);
    }

    @Override
    public String toString() {
        return "IntegrationProperties[redacted]";
    }
}
