package com.prog2.catalog.infrastructure.startup;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import com.prog2.catalog.integration.domain.enums.AttemptOutcome;
import com.prog2.catalog.integration.domain.model.InitializationResult;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.in.InvalidateTechnicalAccessUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;

/**
 * Coordina acceso técnico y snapshot en ese orden, en un único executor dedicado.
 * Recibe el evento de Spring cuando la aplicación está lista y delega la evaluación al caso de uso.
 * La espera ocupa el hilo de integración; el servidor HTTP permanece activo.
 * Tras un fallo terminal o el agotamiento del límite, la recuperación requiere reiniciar Catálogo.
 */
public class CatalogStartupRunner implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogStartupRunner.class);
    private static final int MAX_ATTEMPTS = 3;
    private final InitializeIntegrationUseCase initializeUseCase;
    private final InvalidateTechnicalAccessUseCase invalidation;
    private final SynchronizeCatalogUseCase synchronization;
    private final ExecutorService executor;
    private final Duration retryDelay;
    private Future<?> task;

    /**
     * @param initializeUseCase operación que realiza y evalúa un intento
     * @param invalidation puerto público para cerrar acceso al agotar el ciclo técnico
     * @param synchronization carga completa, ejecutada solo después del acceso técnico disponible
     * @param executor ejecutor dedicado, cuyo cierre queda a cargo de este coordinador
     * @param retryDelay pausa entre intentos recuperables, aplicada solo si queda otro intento
     */
    public CatalogStartupRunner(InitializeIntegrationUseCase initializeUseCase,
            InvalidateTechnicalAccessUseCase invalidation, SynchronizeCatalogUseCase synchronization,
            ExecutorService executor, Duration retryDelay) {
        this.initializeUseCase = initializeUseCase;
        this.invalidation = invalidation;
        this.synchronization = synchronization;
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
            task = executor.submit(this::runStartup);
        }
    }

    private void runStartup() {
        if (!initializeTechnicalAccess() || Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            var result = synchronization.synchronize();
            LOGGER.info("Catalog snapshot: {} ({})", result.phase(), result.safeDiagnostic());
        } catch (RuntimeException _) {
            // Un fallo local de snapshot no revoca por sí mismo un acceso técnico utilizable.
            LOGGER.error("Catalog snapshot failed; recovery requires restart");
        }
    }

    private boolean initializeTechnicalAccess() {
        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
                InitializationResult result = initializeUseCase.initialize();
                LOGGER.info("Technical integration attempt {}/{}: {}", attempt, MAX_ATTEMPTS,
                        result.safeDiagnostic());
                if (result.outcome() != AttemptOutcome.RETRYABLE) {
                    return result.outcome() == AttemptOutcome.AVAILABLE;
                }
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(retryDelay);
                }
            }
            invalidation.invalidate();
            LOGGER.warn("Technical integration unavailable; recovery requires restart");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException _) {
            invalidation.invalidate();
            LOGGER.error("Technical integration initialization failed; recovery requires restart");
        }
        return false;
    }

    /** Solicita detener el executor e interrumpir la tarea, incluida su espera entre intentos. */
    @Override
    public synchronized void close() {
        executor.shutdownNow();
    }
}
