package com.prog2.catalog.integration.infrastructure.persistence.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.prog2.catalog.integration.domain.model.IntegrationSession;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;

/**
 * Adaptador en memoria del puerto Repository para la sesión técnica.
 * Reemplaza una referencia a una sesión inmutable mediante {@link AtomicReference}:
 * los lectores concurrentes observan una sesión completa anterior o nueva.
 * Cada proceso comienza con una sesión inicial y pierde el acceso adquirido al reiniciar.
 */
@Component
public class InMemoryIntegrationSessionAdapter implements IntegrationSessionRepository {

    private final AtomicReference<IntegrationSession> session =
            new AtomicReference<>(IntegrationSession.initial());

    @Override
    public IntegrationSession get() {
        return session.get();
    }

    @Override
    public void replace(IntegrationSession replacement) {
        session.set(Objects.requireNonNull(replacement));
    }
}
