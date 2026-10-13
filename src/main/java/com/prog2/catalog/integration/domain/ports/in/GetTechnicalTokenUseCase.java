package com.prog2.catalog.integration.domain.ports.in;

/** Puerto de entrada para consultar el JWT técnico de una sesión utilizable. */
public interface GetTechnicalTokenUseCase {
    /**
     * Consulta el acceso existente; falla si no está disponible, sin iniciar login ni renovación.
     * El token es un secreto para integración entre backends y nunca se entrega a KMP.
     *
     * @return JWT técnico de la sesión utilizable
     */
    String getToken();
}
