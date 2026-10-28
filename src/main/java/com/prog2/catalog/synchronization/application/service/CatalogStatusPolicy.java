package com.prog2.catalog.synchronization.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import com.prog2.catalog.synchronization.domain.enums.CatalogAvailability;
import com.prog2.catalog.synchronization.domain.model.CatalogState;
import com.prog2.catalog.synchronization.domain.model.CatalogStatus;

/** Aplica D-01 en un único lugar. Los 60 s incluyen el límite y se evalúan al terminar la lectura. */
public class CatalogStatusPolicy {
    private static final Duration MAX_AGE = Duration.ofSeconds(60);
    private final Clock clock;

    public CatalogStatusPolicy(Clock clock) { this.clock = clock; }

    /**
     * @param state metadata confirmada, sin diagnosticar vigencia a partir de su fase solamente
     * @param verified comprobación válida de esta ejecución, no una marca histórica
     * @param accessAvailable acceso técnico actualmente utilizable, comprobado solo en memoria
     * @return estado funcional fechado sin secretos
     */
    public CatalogStatus evaluate(CatalogState state, boolean verified, boolean accessAvailable) {
        Instant now = clock.instant();
        CatalogAvailability availability = switch (state.phase()) {
            case IN_PROGRESS, BUSY -> CatalogAvailability.SYNCHRONIZING;
            case RECOVERY_WAITING -> CatalogAvailability.RECOVERING;
            case FAILED -> CatalogAvailability.FAILED;
            case UNVERIFIABLE -> CatalogAvailability.UNVERIFIABLE;
            default -> remaining(state, verified, accessAvailable, now);
        };
        return status(state, availability, now);
    }

    /** @return metadata de la última copia confirmada, sin acreditar vigencia durante exclusividad */
    public CatalogStatus synchronizing(CatalogState state) {
        return status(state, CatalogAvailability.SYNCHRONIZING, clock.instant());
    }

    private static CatalogAvailability remaining(CatalogState state, boolean verified, boolean accessAvailable, Instant now) {
        if (!state.hasCopy()) { return CatalogAvailability.NO_COPY; }
        boolean checked = state.lastCheckedAt() != null && !state.lastCheckedAt().isAfter(now)
                && Duration.between(state.lastCheckedAt(), now).compareTo(MAX_AGE) <= 0;
        boolean current = state.phase() == com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase.CURRENT
                && verified && accessAvailable && checked
                && Objects.equals(state.appliedVersion(), state.observedVersion());
        return current ? CatalogAvailability.CURRENT : CatalogAvailability.STALE;
    }

    private static CatalogStatus status(CatalogState state, CatalogAvailability availability, Instant now) {
        return new CatalogStatus(availability, state.hasCopy(), state.appliedVersion(), state.observedVersion(),
                state.lastCheckedAt(), now);
    }
}
