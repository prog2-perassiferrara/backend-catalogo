package com.prog2.catalog.synchronization.domain.ports.out;

import com.prog2.catalog.synchronization.domain.model.CatalogConnectionSettings;

/** Frontera para obtener conexiones sin importar modelos ni repositorios de otro slice. */
public interface CatalogConnectionSettingsGateway {
    /** @return configuración adquirida mientras el acceso técnico siga utilizable */
    CatalogConnectionSettings getSettings();
}
