package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.CatalogStatus;

/** Entrada de CU-06; la proyección permitida a cada identidad pertenece al adaptador HTTP. */
public interface GetCatalogStatusUseCase {
    /**
     * @return estado funcional incluso sin copia o con sincronización fallida
     * @throws RuntimeException rechazo tipado de aplicación si no puede determinarse el estado local
     */
    CatalogStatus get();
}
