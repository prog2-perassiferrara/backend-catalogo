package com.prog2.catalog.infrastructure.security.config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * Confianza local de Catálogo: pública RSA de Turnos y orígenes web explícitos.
 * No descarga claves ni acepta material de firma desde una petición. Los errores de
 * configuración omiten valores y causas originales para no volcar archivos sensibles.
 *
 * @param publicKey pública X.509 de Turnos con módulo de al menos 2048 bits
 * @param allowedOrigins orígenes CORS sin comodines ni credenciales
 */
public record JwtSecurityProperties(RSAPublicKey publicKey, List<String> allowedOrigins) {

    private static final String PUBLIC_KEY_SETTING = "JWT_PUBLIC_KEY_PATH";
    private static final String CORS_ORIGINS_SETTING = "CORS_ALLOWED_ORIGINS";

    /**
     * Lee la configuración externa obligatoria antes de habilitar autenticación.
     *
     * @param environment configuración del proceso; no se carga {@code .env} automáticamente
     * @return pública validada y lista inmutable de orígenes, vacía por defecto
     * @throws BeanCreationException si falta la pública o algún valor es inválido
     */
    public static JwtSecurityProperties from(Environment environment) {
        return new JwtSecurityProperties(readPublicKey(environment.getProperty(PUBLIC_KEY_SETTING)),
                readOrigins(environment.getProperty(CORS_ORIGINS_SETTING, "")));
    }

    private static RSAPublicKey readPublicKey(String location) {
        if (location == null || location.isBlank()) {
            throw invalid(PUBLIC_KEY_SETTING);
        }
        try (var input = Files.newInputStream(Path.of(location))) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(input);
            if (key.getModulus().bitLength() < 2048) {
                throw invalid(PUBLIC_KEY_SETTING);
            }
            return key;
        } catch (IOException | RuntimeException _) {
            // No adjuntar la causa: podría incluir ruta, contenido PEM o valores locales.
            throw invalid(PUBLIC_KEY_SETTING);
        }
    }

    private static List<String> readOrigins(String configured) {
        if (configured.isBlank()) {
            return List.of();
        }
        List<String> origins = Arrays.stream(configured.split(",", -1)).map(String::trim).toList();
        for (String origin : origins) {
            try {
                URI uri = URI.create(origin);
                boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
                if (!http || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
                    throw invalid(CORS_ORIGINS_SETTING);
                }
            } catch (IllegalArgumentException _) {
                throw invalid(CORS_ORIGINS_SETTING);
            }
        }
        return origins;
    }

    private static BeanCreationException invalid(String setting) {
        return new BeanCreationException("Invalid or missing security setting: " + setting);
    }

    @Override
    public String toString() {
        return "JwtSecurityProperties[publicKey=RSA, allowedOriginCount=" + allowedOrigins.size() + "]";
    }
}
