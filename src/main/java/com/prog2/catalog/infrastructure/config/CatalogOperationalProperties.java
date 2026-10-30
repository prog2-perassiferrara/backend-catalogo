package com.prog2.catalog.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;

/**
 * Parámetros operativos locales, inmutables durante la ejecución.
 * Los valores predeterminados pertenecen al YAML; los clientes y casos de uso
 * reciben sus valores por constructor, sin depender de esta representación.
 */
public record CatalogOperationalProperties(HttpCycle integration, HttpCycle snapshot,
        Incremental incremental, Recovery recovery, Redis redis,
        Duration kafkaTimeout, Duration monitorInterval) {

    /** Presupuesto de intentos y límites independientes de una solicitud HTTP. */
    public record HttpCycle(int maxAttempts, Duration retryDelay, Duration connectTimeout,
            Duration readTimeout, Duration requestTimeout) { }

    /** Presupuesto del lote incremental y espera antes de habilitar otro ciclo. */
    public record Incremental(int maxAttempts, Duration retryDelay, Duration retryCooldown) { }

    /** Ciclos por bloque y esperas de recuperación sin reiniciar el proceso. */
    public record Recovery(int maxCycles, Duration cycleDelay, Duration blockDelay) { }

    /** Límites de conexión, comando y lectura completa del lote Redis. */
    public record Redis(Duration connectTimeout, Duration commandTimeout, Duration readTimeout) { }

    /**
     * Resuelve los valores efectivos y rechaza configuración inválida sin incluirla en el error.
     *
     * @param environment configuración local, con sobrescrituras por entorno
     * @return parámetros validados, con todos los tiempos expresados como duraciones
     * @throws BeanCreationException si falta un parámetro o no es un entero positivo representable
     */
    public static CatalogOperationalProperties from(Environment environment) {
        return new CatalogOperationalProperties(http(environment, "integration"), http(environment, "snapshot"),
                new Incremental(positive(environment, "incremental.max-attempts"),
                        duration(environment, "incremental.retry-delay-ms"),
                        duration(environment, "incremental.retry-cooldown-ms")),
                new Recovery(positive(environment, "recovery.max-cycles"),
                        duration(environment, "recovery.cycle-delay-ms"),
                        duration(environment, "recovery.block-delay-ms")),
                new Redis(duration(environment, "redis.connect-timeout-ms"),
                        duration(environment, "redis.command-timeout-ms"),
                        duration(environment, "redis.read-timeout-ms")),
                duration(environment, "kafka.timeout-ms"), duration(environment, "monitor.interval-ms"));
    }

    private static HttpCycle http(Environment environment, String group) {
        return new HttpCycle(positive(environment, group + ".max-attempts"),
                duration(environment, group + ".retry-delay-ms"),
                duration(environment, group + ".connect-timeout-ms"),
                duration(environment, group + ".read-timeout-ms"),
                duration(environment, group + ".request-timeout-ms"));
    }

    private static Duration duration(Environment environment, String suffix) {
        return Duration.ofMillis(positive(environment, suffix));
    }

    private static int positive(Environment environment, String suffix) {
        String key = "catalog.operational." + suffix;
        try {
            int value = Integer.parseInt(environment.getProperty(key));
            if (value > 0) { return value; }
        } catch (IllegalArgumentException _) {
            // No propagar la causa: el mensaje del conversor puede reproducir el valor recibido.
        }
        throw new BeanCreationException("catalogOperationalProperties", "Invalid operational setting: " + key);
    }
}
