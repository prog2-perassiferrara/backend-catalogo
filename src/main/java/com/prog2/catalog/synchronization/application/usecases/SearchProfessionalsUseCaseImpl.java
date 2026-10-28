package com.prog2.catalog.synchronization.application.usecases;

import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.CatalogStatusPolicy;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import com.prog2.catalog.synchronization.domain.enums.CatalogQueryFailure;
import com.prog2.catalog.synchronization.domain.exception.CatalogReadException;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogQueryRepository;
import com.prog2.catalog.synchronization.domain.ports.out.TechnicalAccessGateway;
import com.prog2.catalog.synchronization.domain.ports.in.SearchProfessionalsUseCase;

/** CU-04: resuelve una página local incluso si la copia requiere aviso de desactualización. */
public class SearchProfessionalsUseCaseImpl implements SearchProfessionalsUseCase {
    private final CatalogQueryRepository repository;
    private final TechnicalAccessGateway access;
    private final CatalogSynchronizationGuard guard;
    private final CatalogStatusPolicy policy;

    public SearchProfessionalsUseCaseImpl(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        this.repository = repository;
        this.access = access;
        this.guard = guard;
        this.policy = policy;
    }

    @Override
    public ProfessionalSearchResult search(ProfessionalSearchCriteria criteria) {
        if (criteria == null || !criteria.isValid()) {
            throw new CatalogQueryException(CatalogQueryFailure.INVALID_SEARCH_CRITERIA);
        }
        boolean readLocked = !guard.verificationRequired() && guard.tryRead();
        try {
            CatalogSearchView view = repository.search(criteria);
            if (!view.catalog().hasCopy()) { throw new CatalogQueryException(CatalogQueryFailure.CATALOG_COPY_UNAVAILABLE); }
            return new ProfessionalSearchResult(view.content(), criteria.page(), criteria.size(), view.totalElements(),
                    status(view.catalog(), readLocked));
        } catch (CatalogReadException _) {
            throw new CatalogQueryException(CatalogQueryFailure.CATALOG_UNAVAILABLE);
        } finally {
            if (readLocked) { guard.endRead(); }
        }
    }

    private CatalogStatus status(CatalogState state, boolean readLocked) {
        if (guard.isSynchronizing()) { return policy.synchronizing(state); }
        return policy.evaluate(state, readLocked && !guard.verificationRequired(), accessAvailable());
    }

    private boolean accessAvailable() {
        try { access.getToken(); return true; }
        catch (CatalogSnapshotException _) { return false; }
    }
}

