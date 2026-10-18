package com.prog2.catalog.synchronization.domain.ports.out;

import com.prog2.catalog.synchronization.domain.model.CatalogSnapshot;

/** Puerto de descarga, independiente del transporte HTTP. */
public interface CatalogSnapshotGateway {
    /**
     * Obtiene una copia completa con el acceso técnico existente, sin autenticar ni reintentar.
     *
     * @param token JWT técnico secreto, nunca debe registrarse
     * @return copia interpretada y validada
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException ante un fallo clasificado
     */
    CatalogSnapshot fetch(String token);
}
