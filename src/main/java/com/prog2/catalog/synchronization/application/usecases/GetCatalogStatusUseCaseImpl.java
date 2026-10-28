package com.prog2.catalog.synchronization.application.usecases;

import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.domain.enums.CatalogQueryFailure;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.GetCatalogStatusUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/** CU-06: permanece disponible durante sincronización leyendo únicamente metadata confirmada. */
public class GetCatalogStatusUseCaseImpl implements GetCatalogStatusUseCase {
    private final CatalogQueryRepository repository;
    private final TechnicalAccessGateway access;
    private final CatalogSynchronizationGuard guard;
    private final CatalogStatusPolicy policy;

    public GetCatalogStatusUseCaseImpl(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        this.repository = repository;
        this.access = access;
        this.guard = guard;
        this.policy = policy;
    }

    @Override
    public CatalogStatus get() {
        // Sin verificación inicial, solo metadata y sin disputar el acceso del arranque.
        boolean readLocked = !guard.verificationRequired() && guard.tryRead();
        try {
            CatalogState state = repository.readState();
            if (guard.isSynchronizing()) { return policy.synchronizing(state); }
            return policy.evaluate(state, readLocked && !guard.verificationRequired(), accessAvailable());
        } catch (CatalogReadException _) {
            throw new CatalogQueryException(CatalogQueryFailure.CATALOG_UNAVAILABLE);
        } finally {
            if (readLocked) { guard.endRead(); }
        }
    }

    private boolean accessAvailable() {
        try { access.getToken(); return true; }
        catch (CatalogSnapshotException _) { return false; }
    }
}
