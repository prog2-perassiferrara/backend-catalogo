package com.prog2.catalog.synchronization.domain.ports.out;

import com.prog2.catalog.synchronization.domain.exception.CatalogReadException;
import com.prog2.catalog.synchronization.domain.model.CatalogReadView;
import com.prog2.catalog.synchronization.domain.model.CatalogState;
import com.prog2.catalog.synchronization.domain.model.CatalogSearchView;
import com.prog2.catalog.synchronization.domain.model.CatalogCategoriesView;
import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchCriteria;

/** Repository de lectura: devuelve valores materializados, sin escrituras ni consultas centrales. */
public interface CatalogQueryRepository {
    /**
     * @param criteria filtros y paginación previamente validados
     * @return página, categorías y total de la misma copia que la metadata; sin copia, colecciones vacías
     * @throws CatalogReadException si falla cualquier parte de la lectura coherente
     */
    CatalogSearchView search(ProfessionalSearchCriteria criteria);

    /**
     * @return categorías completas ordenadas por nombre e ID y su metadata confirmada
     * @throws CatalogReadException si no puede completarse la lectura
     */
    CatalogCategoriesView readCategories();

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
