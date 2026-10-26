package com.prog2.catalog.synchronization.infrastructure.messaging;

import java.time.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.infrastructure.config.CatalogJsonMapperFactory;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.infrastructure.messaging.dto.CatalogUpdatedResponse;

/** Adapter de entrada: valida el mensaje, delega CU-02 y confirma solo un reconocimiento durable. */
public class CatalogUpdatedListener implements AcknowledgingMessageListener<String, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogUpdatedListener.class);
    private final UpdateCatalogUseCase updates;
    private final JsonMapper json;
    private final Clock clock;
    private final Runnable stopConsumer;

    public CatalogUpdatedListener(UpdateCatalogUseCase updates, JsonMapper json, Clock clock, Runnable stopConsumer) {
        this.updates = updates;
        this.json = CatalogJsonMapperFactory.strict(json);
        this.clock = clock;
        this.stopConsumer = stopConsumer;
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> message, Acknowledgment acknowledgment) {
        CatalogNotification notification = parse(message.value());
        CatalogUpdateResult result = notification == null
                ? updates.reject(new RejectedCatalogRecord(message.topic(), message.partition(), message.offset(),
                        "INCOMPATIBLE_CATALOG_NOTIFICATION"))
                : updates.process(notification);
        if (result.recorded()) {
            try { acknowledgment.acknowledge(); }
            catch (RuntimeException _) {
                // El recibo ya es durable. Reentregar es seguro si se perdió el commit del offset.
                LOGGER.warn("CATALOG_OFFSET_CONFIRMATION_UNAVAILABLE");
                acknowledgment.nack(Duration.ofSeconds(30));
            }
        } else if (result.terminal()) {
            LOGGER.error("Catalog consumer stopped: {}", result.safeDiagnostic());
            stopConsumer.run();
        } else {
            Duration delay = result.retryAt() == null ? Duration.ofSeconds(5)
                    : Duration.between(clock.instant(), result.retryAt());
            acknowledgment.nack(delay.isNegative() || delay.isZero() ? Duration.ofSeconds(1) : delay);
        }
    }

    private CatalogNotification parse(String body) {
        try {
            var dto = json.readValue(body, CatalogUpdatedResponse.class);
            if (dto == null || !Integer.valueOf(1).equals(dto.schemaVersion())
                    || !"CatalogUpdated".equals(dto.eventType()) || dto.newVersion() == null) { return null; }
            return new CatalogNotification(dto.eventId(), dto.newVersion(), Instant.parse(dto.occurredAt()), dto.source());
        } catch (RuntimeException _) {
            return null;
        }
    }
}
