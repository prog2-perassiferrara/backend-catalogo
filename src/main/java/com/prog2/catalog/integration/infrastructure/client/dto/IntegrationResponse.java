package com.prog2.catalog.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.prog2.catalog.integration.domain.enums.ProvisioningStatus;
import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;

/**
 * DTO externo de configuración, presente en el login y devuelto sin envoltorio por el GET actual.
 * Conserva solo los campos requeridos por Catálogo e ignora campos adicionales compatibles.
 * {@link #toDomain()} traduce la respuesta al modelo interno para aislar el contrato JSON.
 * Los datos Redis contienen secretos; la representación de texto oculta todos los valores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntegrationResponse(String groupId, String provisioningStatus, String redisHost,
        Integer redisPort, String redisUsername, String redisPassword, String redisReadNamespace,
        String kafkaBootstrapServers, String kafkaConsumerGroupId, String kafkaCatalogTopic) {

    /**
     * Convierte los campos externos sin decidir todavía si la sesión puede habilitarse.
     *
     * @return configuración de dominio con el estado de aprovisionamiento interpretado
     * @throws IllegalArgumentException si el estado externo no corresponde a un valor conocido
     * @throws NullPointerException si falta el estado de aprovisionamiento
     */
    public IntegrationConfiguration toDomain() {
        return new IntegrationConfiguration(groupId, ProvisioningStatus.valueOf(provisioningStatus),
                redisHost, redisPort, redisUsername, redisPassword, redisReadNamespace,
                kafkaBootstrapServers, kafkaConsumerGroupId, kafkaCatalogTopic);
    }

    @Override
    public String toString() {
        return "IntegrationResponse[redacted]";
    }
}
