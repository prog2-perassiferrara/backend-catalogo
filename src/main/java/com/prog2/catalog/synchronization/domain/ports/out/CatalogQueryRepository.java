package com.prog2.catalog.synchronization.domain.ports.out;

import com.prog2.catalog.synchronization.domain.exception.CatalogReadException;
import com.prog2.catalog.synchronization.domain.model.CatalogReadView;
import com.prog2.catalog.synchronization.domain.model.CatalogState;

/** Repository de lectura: devuelve valores materializados, sin escrituras ni consultas centrales. */
public interface CatalogQueryRepository {
    /**
     * @param professionalId ID positivo del profesional
     * @return datos y metadata pertenecientes a una misma vista confirmada
     * @throws CatalogReadException si no puede completarse la lectura coherente
     */
    CatalogReadView readProfessional(long professionalId);

    /**
     * @return metadata confirmada, legible también durante una escritura sin esperar su commit
     * @throws CatalogReadException si no puede leerse el estado
     */
    CatalogState readState();
}
