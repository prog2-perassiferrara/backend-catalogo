package com.prog2.catalog.integration.application.usecases;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.prog2.catalog.integration.domain.enums.AttemptOutcome;
import com.prog2.catalog.integration.domain.enums.IntegrationState;
import com.prog2.catalog.integration.domain.enums.ProvisioningStatus;
import com.prog2.catalog.integration.domain.exception.CentralIntegrationException;
import com.prog2.catalog.integration.domain.model.InitializationResult;
import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/**
 * Evalúa un intento de acceso técnico y publica la sesión cuando resulta utilizable.
 * Aplica inversión de dependencias al recibir los puertos de cátedra y almacenamiento;
 * el coordinador de infraestructura decide cuándo repetir un resultado recuperable.
 */
@Component
public class InitializeIntegrationUseCaseImpl implements InitializeIntegrationUseCase {

    private final CentralIntegrationGateway gateway;
    private final IntegrationSessionRepository sessions;
    private final Clock clock;

    public InitializeIntegrationUseCaseImpl(CentralIntegrationGateway gateway,
            IntegrationSessionRepository sessions, Clock clock) {
        this.gateway = gateway;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public InitializationResult initialize() {
        IntegrationSession previous = sessions.get();
        if (previous.state() == IntegrationState.FAILED || previous.isExpired(clock.instant())) {
            return fail("ACCESS_UNAVAILABLE");
        }
        if (previous.isUsable(clock.instant())) {
            return new InitializationResult(AttemptOutcome.AVAILABLE, "READY");
        }
        try {
            IntegrationSession acquired;
            if (previous.technicalToken() == null) {
                acquired = gateway.authenticate();
            } else {
                // Un login PENDING ya obtuvo JWT: consultar el aprovisionamiento con ese mismo acceso.
                acquired = new IntegrationSession(previous.technicalToken(), previous.expiresAt(),
                        IntegrationState.INITIALIZING,
                        gateway.getCurrentIntegration(previous.technicalToken()));
            }
            if (acquired.isExpired(clock.instant())) {
                return fail("TOKEN_EXPIRED");
            }
            IntegrationConfiguration configuration = acquired.configuration();
            if (configuration.provisioningStatus() == ProvisioningStatus.PENDING) {
                sessions.replace(acquired);
                return new InitializationResult(AttemptOutcome.RETRYABLE, "PROVISIONING_PENDING");
            }
            if (!configuration.isComplete()) {
                return fail("PROVISIONING_OR_CONFIGURATION_INVALID");
            }
            sessions.replace(new IntegrationSession(acquired.technicalToken(), acquired.expiresAt(),
                    IntegrationState.READY, configuration));
            return new InitializationResult(AttemptOutcome.AVAILABLE, "READY");
        } catch (CentralIntegrationException failure) {
            if (failure.isRetryable()) {
                return new InitializationResult(AttemptOutcome.RETRYABLE, failure.getMessage());
            }
            return fail(failure.getMessage());
        }
    }

    private InitializationResult fail(String safeDiagnostic) {
        sessions.replace(IntegrationSession.failed());
        return new InitializationResult(AttemptOutcome.TERMINAL, safeDiagnostic);
    }
}
