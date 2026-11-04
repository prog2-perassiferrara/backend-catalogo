package com.prog2.catalog.synchronization.infrastructure.persistence.adapter;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.prog2.catalog.synchronization.domain.exception.CatalogReadException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogQueryRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.TransactionalCatalogQueryReader;

/** Adapter del Repository de consultas. Traduce también fallos al salir del proxy, sin filtrar SQL. */
@Component
@Slf4j
public class JpaCatalogQueryAdapter implements CatalogQueryRepository {
    private final TransactionalCatalogQueryReader reader;

    public JpaCatalogQueryAdapter(TransactionalCatalogQueryReader reader) { this.reader = reader; }

    @Override
    public CatalogSearchView search(ProfessionalSearchCriteria criteria) { return read(() -> reader.search(criteria)); }

    @Override
    public CatalogCategoriesView readCategories() { return read(reader::readCategories); }

    @Override
    public CatalogReadView readProfessional(long professionalId) {
        return read(() -> reader.readProfessional(professionalId));
    }

    @Override
    public CatalogState readState() { return read(reader::readState); }

    private static <T> T read(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException _) {
            log.warn("Catalog read unavailable: CATALOG_UNAVAILABLE");
            throw new CatalogReadException();
        }
    }
}
