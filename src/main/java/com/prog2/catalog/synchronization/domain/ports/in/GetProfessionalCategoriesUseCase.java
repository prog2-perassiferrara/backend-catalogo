package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.ProfessionalCategoriesResult;

/** Categorías locales disponibles para construir el filtro de CU-04 en KMP. */
public interface GetProfessionalCategoriesUseCase {
    /**
     * @return categorías ordenadas con su habilitación y el estado de la copia consultada
     * @throws RuntimeException rechazo tipado cuando no hay copia o no se puede leer
     */
    ProfessionalCategoriesResult get();
}

