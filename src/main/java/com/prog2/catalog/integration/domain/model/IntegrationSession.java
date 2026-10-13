package com.prog2.catalog.integration.domain.model;

import java.time.Instant;

import com.prog2.catalog.integration.domain.enums.IntegrationState;

/**
 * Sesión técnica inmutable, conservada exclusivamente en memoria como una unidad completa.
 * Su representación de texto muestra solo el estado para evitar divulgar el acceso técnico.
 *
 * @param technicalToken JWT secreto adquirido por login, o {@code null} si no hay acceso
 * @param expiresAt vencimiento UTC leído de {@code exp}, o {@code null} si no fue informado
 * @param state estado local de habilitación de la sesión
 * @param configuration configuración de cátedra, o {@code null} en sesiones iniciales o fallidas
 */
public record IntegrationSession(String technicalToken, Instant expiresAt,
        IntegrationState state, IntegrationConfiguration configuration) {

    /** @return sesión inicial sin token ni configuración */
    public static IntegrationSession initial() {
        return new IntegrationSession(null, null, IntegrationState.INITIALIZING, null);
    }

    /** @return sesión fallida que descarta token y configuración */
    public static IntegrationSession failed() {
        return new IntegrationSession(null, null, IntegrationState.FAILED, null);
    }

    /**
     * Comprueba el límite de vencimiento inclusive; la ausencia de {@code exp} no inventa un límite.
     *
     * @param now instante de evaluación
     * @return si se conoce un vencimiento y ya se alcanzó
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * Evalúa conjuntamente estado, presencia de token, configuración y vencimiento conocido.
     * Esta comprobación no verifica la firma del JWT ni la conectividad Redis/Kafka.
     *
     * @param now instante de evaluación
     * @return si la sesión reúne las condiciones locales para utilizar el acceso técnico
     */
    public boolean isUsable(Instant now) {
        return state == IntegrationState.READY && technicalToken != null && !technicalToken.isBlank()
                && configuration != null && configuration.isComplete() && !isExpired(now);
    }

    @Override
    public String toString() {
        return "IntegrationSession[state=" + state + "]";
    }
}
