package com.prog2.catalog.integration.infrastructure.config;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import com.prog2.catalog.integration.domain.enums.AttemptOutcome;
import com.prog2.catalog.integration.domain.model.InitializationResult;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/**
 * Coordina al arrancar un único ciclo de hasta tres intentos en un executor dedicado.
 * Recibe el evento de Spring cuando la aplicación está lista y delega la evaluación al caso de uso.
 * La espera ocupa el hilo de integración; el servidor HTTP permanece activo.
 * Tras un fallo terminal o el agotamiento del límite, la recuperación requiere reiniciar Catálogo.
 */
public class IntegrationStartupRunner implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationStartupRunner.class);
    private static final int MAX_ATTEMPTS = 3;
    private final InitializeIntegrationUseCase initializeUseCase;
    private final IntegrationSessionRepository sessions;
    private final ExecutorService executor;
    private final Duration retryDelay;
    private Future<?> task;

    /**
     * @param initializeUseCase operación que realiza y evalúa un intento
     * @param sessions almacenamiento que permite cerrar el acceso al agotar el ciclo
     * @param executor ejecutor dedicado, cuyo cierre queda a cargo de este coordinador
     * @param retryDelay pausa entre intentos recuperables, aplicada solo si queda otro intento
     */
    public IntegrationStartupRunner(InitializeIntegrationUseCase initializeUseCase,
            IntegrationSessionRepository sessions, ExecutorService executor, Duration retryDelay) {
        this.initializeUseCase = initializeUseCase;
        this.sessions = sessions;
        this.executor = executor;
        this.retryDelay = retryDelay;
    }

    /**
     * Envía el ciclo al executor una sola vez, aunque Spring vuelva a publicar el evento.
     * La sincronización protege conjuntamente la comprobación y asignación de la tarea.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        if (task == null) {
            task = executor.submit(this::runAttempts);
        }
    }

    private void runAttempts() {
        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                InitializationResult result = initializeUseCase.initialize();
                LOGGER.info("Technical integration attempt {}/{}: {}", attempt, MAX_ATTEMPTS,
                        result.safeDiagnostic());
                if (result.outcome() != AttemptOutcome.RETRYABLE) {
                    return;
                }
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(retryDelay);
                }
            }
            sessions.replace(IntegrationSession.failed());
            LOGGER.warn("Technical integration unavailable; recovery requires restart");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            sessions.replace(IntegrationSession.failed());
            LOGGER.error("Technical integration initialization failed; recovery requires restart");
        }
    }

    /** Solicita detener el executor e interrumpir la tarea, incluida su espera entre intentos. */
    @Override
    public synchronized void close() {
        executor.shutdownNow();
    }
}
