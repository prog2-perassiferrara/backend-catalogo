package com.prog2.catalog.synchronization.infrastructure.integration.adapter;

import org.springframework.stereotype.Component;
import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.integration.domain.ports.in.InvalidateTechnicalAccessUseCase;
import com.prog2.catalog.integration.domain.ports.in.GetIntegrationConfigurationUseCase;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.ports.out.TechnicalAccessGateway;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogConnectionSettingsGateway;
import com.prog2.catalog.synchronization.domain.model.CatalogConnectionSettings;

/**
 * Adapter entre slices aprobado: consume solo puertos públicos de integration.
 * Traduce su indisponibilidad al contrato propio de synchronization, sin leer repositorios ajenos.
 */
@Component
public class IntegrationTechnicalAccessAdapter implements TechnicalAccessGateway, CatalogConnectionSettingsGateway {
    private final GetTechnicalTokenUseCase tokens;
    private final InvalidateTechnicalAccessUseCase invalidation;
    private final GetIntegrationConfigurationUseCase configuration;

    public IntegrationTechnicalAccessAdapter(GetTechnicalTokenUseCase tokens,
            InvalidateTechnicalAccessUseCase invalidation, GetIntegrationConfigurationUseCase configuration) {
        this.tokens = tokens;
        this.invalidation = invalidation;
        this.configuration = configuration;
    }

    @Override
    public String getToken() {
        try {
            return tokens.getToken();
        } catch (IntegrationUnavailableException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "TECHNICAL_ACCESS_UNAVAILABLE");
        }
    }

    @Override
    public void invalidate() {
        invalidation.invalidate();
    }

    @Override
    public CatalogConnectionSettings getSettings() {
        try {
            var value = configuration.getConfiguration();
            return new CatalogConnectionSettings(value.redisHost(), value.redisPort(), value.redisUsername(),
                    value.redisPassword(), value.redisReadNamespace(), value.kafkaBootstrapServers(),
                    value.kafkaConsumerGroupId(), value.kafkaCatalogTopic());
        } catch (IntegrationUnavailableException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "TECHNICAL_ACCESS_UNAVAILABLE");
        }
    }
}
