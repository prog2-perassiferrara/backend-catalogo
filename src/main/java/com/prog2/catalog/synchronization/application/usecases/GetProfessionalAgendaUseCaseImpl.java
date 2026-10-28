package com.prog2.catalog.synchronization.application.usecases;

import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.GetProfessionalAgendaUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/** CU-05: comprueba la vigencia de la misma lectura que entrega, sin consultar a cátedra. */
public class GetProfessionalAgendaUseCaseImpl implements GetProfessionalAgendaUseCase {
    private final CatalogQueryRepository repository;
    private final TechnicalAccessGateway access;
    private final CatalogSynchronizationGuard guard;
    private final CatalogStatusPolicy policy;

    public GetProfessionalAgendaUseCaseImpl(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        this.repository = repository;
        this.access = access;
        this.guard = guard;
        this.policy = policy;
    }

    @Override
    public ProfessionalAgenda get(long professionalId) {
        if (professionalId <= 0) { throw new CatalogQueryException(CatalogQueryFailure.INVALID_PROFESSIONAL_ID); }
        // El arranque necesita primero el escritor, sin lectores ocupando su bloqueo.
        if (guard.verificationRequired() || !guard.tryRead()) {
            throw new CatalogQueryException(CatalogQueryFailure.CATALOG_NOT_CURRENT);
        }
        try {
            CatalogReadView view = repository.readProfessional(professionalId);
            CatalogStatus status = policy.evaluate(view.catalog(), !guard.verificationRequired(), accessAvailable());
            if (status.state() != CatalogAvailability.CURRENT) {
                throw new CatalogQueryException(CatalogQueryFailure.CATALOG_NOT_CURRENT);
            }
            Professional professional = view.professional().orElseThrow(
                    () -> new CatalogQueryException(CatalogQueryFailure.PROFESSIONAL_NOT_FOUND));
            return new ProfessionalAgenda(professional, view.weeklySchedules(), status);
        } catch (CatalogReadException _) {
            throw new CatalogQueryException(CatalogQueryFailure.CATALOG_UNAVAILABLE);
        } finally { guard.endRead(); }
    }

    private boolean accessAvailable() {
        try { access.getToken(); return true; }
        catch (CatalogSnapshotException _) { return false; }
    }
}
