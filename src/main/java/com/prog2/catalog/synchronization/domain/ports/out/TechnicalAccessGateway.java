package com.prog2.catalog.synchronization.domain.ports.out;

/** Puerto propio para colaborar con la sesión técnica sin acceder a su almacenamiento. */
public interface TechnicalAccessGateway {
    /**
     * @return JWT técnico utilizable
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException si no hay acceso vigente
     */
    String getToken();

    /** Descarta el acceso tras un rechazo 401; no renueva ni vuelve a iniciar sesión. */
    void invalidate();
}
