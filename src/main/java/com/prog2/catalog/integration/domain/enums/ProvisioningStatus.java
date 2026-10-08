package com.prog2.catalog.integration.domain.enums;

/** Estado de aprovisionamiento comunicado por cátedra, independiente del estado local de sesión. */
public enum ProvisioningStatus {
    /** Cátedra todavía está preparando los recursos de integración. */
    PENDING,
    /** Recursos aprovisionados; Catálogo debe comprobar también la configuración y el JWT. */
    PROVISIONED,
    /** Aprovisionamiento fallido, tratado como terminal. */
    FAILED,
    /** Integración revocada, tratada como terminal. */
    REVOKED
}
