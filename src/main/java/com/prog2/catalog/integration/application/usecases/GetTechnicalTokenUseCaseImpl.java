package com.prog2.catalog.integration.application.usecases;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/**
 * Consulta el acceso técnico existente y descarta la sesión al detectar su vencimiento.
 * Depende del puerto de almacenamiento y de un reloj inyectado para evaluar la vigencia.
 * La entrega HTTP protegida a Turnos se incorpora en la funcionalidad de seguridad.
 */
@Component
public class GetTechnicalTokenUseCaseImpl implements GetTechnicalTokenUseCase {

    private final IntegrationSessionRepository sessions;
    private final Clock clock;

    public GetTechnicalTokenUseCaseImpl(IntegrationSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IntegrationUnavailableException si la sesión no está habilitada o se detecta vencida
     */
    @Override
    public String getToken() {
        IntegrationSession session = sessions.get();
        if (!session.isUsable(clock.instant())) {
            if (session.isExpired(clock.instant())) {
                sessions.replace(IntegrationSession.failed());
            }
            throw new IntegrationUnavailableException();
        }
        return session.technicalToken();
    }
}
