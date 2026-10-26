package com.prog2.catalog.synchronization.domain.model;

/** Configuración local del slice, adaptada desde el puerto público de integration. Contiene secretos. */
public record CatalogConnectionSettings(String redisHost, int redisPort, String redisUsername,
        String redisPassword, String redisReadNamespace, String kafkaBootstrapServers,
        String kafkaConsumerGroupId, String kafkaCatalogTopic) {
    @Override
    public String toString() {
        return "CatalogConnectionSettings[redacted]";
    }
}
