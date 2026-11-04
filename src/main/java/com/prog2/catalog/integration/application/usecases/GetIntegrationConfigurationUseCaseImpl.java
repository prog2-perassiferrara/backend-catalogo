package com.prog2.catalog.integration.application.usecases;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import org.springframework.stereotype.Component;
import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.domain.model.*;
import com.prog2.catalog.integration.domain.ports.in.GetIntegrationConfigurationUseCase;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/** Consulta la sesión completa y descarta su acceso si venció, igual que la consulta del token. */
@Component
@RequiredArgsConstructor
public class GetIntegrationConfigurationUseCaseImpl implements GetIntegrationConfigurationUseCase {
    private final IntegrationSessionRepository sessions;
    private final Clock clock;


    @Override
    public IntegrationConfiguration getConfiguration() {
        IntegrationSession session = sessions.get();
        if (!session.isUsable(clock.instant())) {
            if (session.isExpired(clock.instant())) {
                sessions.replace(IntegrationSession.failed());
            }
            throw new IntegrationUnavailableException();
        }
        return session.configuration();
    }
}
