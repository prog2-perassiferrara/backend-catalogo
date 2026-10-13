package com.prog2.catalog.integration.domain.model;

import com.prog2.catalog.integration.domain.enums.ProvisioningStatus;

/**
 * Configuración de cátedra necesaria para las futuras operaciones de Catálogo.
 * Contiene secretos Redis; {@code toString()} muestra únicamente el aprovisionamiento.
 *
 * @param groupId identidad del grupo técnico, independiente del usuario final
 * @param provisioningStatus estado de preparación de recursos comunicado por cátedra
 * @param redisHost servidor Redis asignado
 * @param redisPort puerto Redis válido entre 1 y 65535
 * @param redisUsername usuario técnico Redis
 * @param redisPassword contraseña secreta Redis, disponible al completar el aprovisionamiento
 * @param redisReadNamespace namespace autorizado para lectura de cambios
 * @param kafkaBootstrapServers servidores iniciales de conexión Kafka
 * @param kafkaConsumerGroupId grupo consumidor asignado a Catálogo
 * @param kafkaCatalogTopic topic que notifica cambios de catálogo
 */
public record IntegrationConfiguration(String groupId, ProvisioningStatus provisioningStatus,
        String redisHost, Integer redisPort, String redisUsername, String redisPassword,
        String redisReadNamespace, String kafkaBootstrapServers, String kafkaConsumerGroupId,
        String kafkaCatalogTopic) {

    /**
     * Exige aprovisionamiento completo y los parámetros necesarios presentes.
     * No realiza conexiones para comprobar credenciales o disponibilidad de Redis/Kafka.
     *
     * @return si la configuración cumple las condiciones locales para habilitar el acceso
     */
    public boolean isComplete() {
        return hasText(groupId) && provisioningStatus == ProvisioningStatus.PROVISIONED
                && hasText(redisHost) && redisPort != null && redisPort > 0 && redisPort <= 65535
                && hasText(redisUsername) && hasText(redisPassword) && hasText(redisReadNamespace)
                && hasText(kafkaBootstrapServers) && hasText(kafkaConsumerGroupId)
                && hasText(kafkaCatalogTopic);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "IntegrationConfiguration[provisioningStatus=" + provisioningStatus + "]";
    }
}
