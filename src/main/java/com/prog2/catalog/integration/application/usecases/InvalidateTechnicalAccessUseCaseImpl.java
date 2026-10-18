package com.prog2.catalog.integration.application.usecases;

import org.springframework.stereotype.Component;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.in.InvalidateTechnicalAccessUseCase;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/** Cierra el acceso dentro del slice propietario de la sesión, sin efectuar HTTP. */
@Component
public class InvalidateTechnicalAccessUseCaseImpl implements InvalidateTechnicalAccessUseCase {
    private final IntegrationSessionRepository sessions;

    public InvalidateTechnicalAccessUseCaseImpl(IntegrationSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public void invalidate() {
        sessions.replace(IntegrationSession.failed());
    }
}
