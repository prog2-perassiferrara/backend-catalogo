package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchCriteria;
import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchResult;

/** CU-04: buscar en la copia local, admitiendo datos anteriores con aviso. */
public interface SearchProfessionalsUseCase {
    /**
     * @param criteria filtros opcionales y paginación dentro de los límites acordados
     * @return página, total y estado de esa misma consulta; ausencia de coincidencias es válida
     * @throws RuntimeException rechazo tipado de entrada inválida, falta de copia o lectura fallida
     */
    ProfessionalSearchResult search(ProfessionalSearchCriteria criteria);
}

