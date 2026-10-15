package com.prog2.catalog.infrastructure.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida las reglas propias del JWT de Turnos después de verificar su firma.
 * Implementa la estrategia de validación de Spring, complementando sus validadores
 * de emisor, audiencia y tiempo. No decide permisos de rutas ni acepta tokens de cátedra.
 */
public final class JwtContractValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration SERVICE_LIFETIME = Duration.ofMinutes(5);
    private static final Pattern AUTHORITIES = Pattern.compile("ROLE_[A-Z0-9_]++(?: ROLE_[A-Z0-9_]++)*+");
    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "JWT does not satisfy the agreed contract", null));

    private final Clock clock;

    /** @param clock reloj compartido para evaluar emisión futura de forma determinista */
    public JwtContractValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Exige sujeto, perfil, autoridades como cadena y tiempos coherentes.
     * Una cadena vacía de autoridades es válida, pero no concede acceso a operaciones.
     * La identidad y el permiso concretos de servicio se comprueban al autorizar la ruta.
     *
     * @param jwt token con firma verificada por el decoder
     * @return éxito o error genérico sin copiar claims del solicitante
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subject = jwt.getClaims().get("sub");
        Object profile = jwt.getClaims().get("token_use");
        Object authorities = jwt.getClaims().get("auth");
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        if (!(subject instanceof String value) || value.isBlank()
                || !("user".equals(profile) || "service".equals(profile))
                || !(authorities instanceof String roles)
                || (!roles.isEmpty() && !AUTHORITIES.matcher(roles).matches())
                || issued == null || expires == null || !expires.isAfter(issued)
                || issued.isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return INVALID;
        }
        if ("service".equals(profile) && Duration.between(issued, expires).compareTo(SERVICE_LIFETIME) > 0) {
            return INVALID;
        }
        return OAuth2TokenValidatorResult.success();
    }
}
