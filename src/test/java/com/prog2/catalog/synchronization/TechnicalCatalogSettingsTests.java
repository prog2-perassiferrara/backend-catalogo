package com.prog2.catalog.synchronization;

import java.time.*;
import org.junit.jupiter.api.Test;
import com.prog2.catalog.integration.application.usecases.*;
import com.prog2.catalog.integration.domain.enums.*;
import com.prog2.catalog.integration.domain.model.*;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.infrastructure.integration.adapter.IntegrationTechnicalAccessAdapter;
import static org.assertj.core.api.Assertions.*;

class TechnicalCatalogSettingsTests {
    private final Clock clock = Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC);
    private final InMemoryIntegrationSessionAdapter sessions = new InMemoryIntegrationSessionAdapter();

    private IntegrationTechnicalAccessAdapter access(Clock time) {
        return new IntegrationTechnicalAccessAdapter(new GetTechnicalTokenUseCaseImpl(sessions, time),
                new InvalidateTechnicalAccessUseCaseImpl(sessions), new GetIntegrationConfigurationUseCaseImpl(sessions, time));
    }

    @Test
    void adaptsOnlyAUsableSessionWithoutExposingSecretsInText() {
        var configuration = new IntegrationConfiguration("fictional-group", ProvisioningStatus.PROVISIONED,
                "redis.test", 6379, "fictional-user", "fictional-redis-access", "catedra:sync:*",
                "kafka.test:9092", "fictional-group", "catedra.catalog.fictional");
        sessions.replace(new IntegrationSession("fictional-technical-access", clock.instant().plusSeconds(60),
                IntegrationState.READY, configuration));
        var settings = access(clock).getSettings();
        assertThat(settings.redisHost()).isEqualTo("redis.test");
        assertThat(settings.kafkaCatalogTopic()).isEqualTo("catedra.catalog.fictional");
        assertThat(settings.redisPassword()).isEqualTo(configuration.redisPassword());
        assertThat(settings.toString()).doesNotContain(configuration.redisPassword(), configuration.redisUsername());
        assertThat(sessions.get().configuration()).isSameAs(configuration);
    }

    @Test
    void rejectsUnavailableConfigurationAndDiscardsExpiredSession() {
        var unavailable = access(clock);
        assertThatThrownBy(unavailable::getSettings).isInstanceOfSatisfying(CatalogSnapshotException.class,
                failure -> assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.UNAUTHORIZED));
        var configuration = new IntegrationConfiguration("fictional-group", ProvisioningStatus.PROVISIONED,
                "redis.test", 6379, "fictional-user", "fictional-redis-access", "catedra:sync:*",
                "kafka.test:9092", "fictional-group", "catedra.catalog.fictional");
        sessions.replace(new IntegrationSession("fictional-technical-access", clock.instant().plusSeconds(60),
                IntegrationState.READY, configuration));
        var expired = access(Clock.offset(clock, Duration.ofSeconds(60)));
        assertThatThrownBy(expired::getSettings)
                .isInstanceOf(CatalogSnapshotException.class);
        assertThat(sessions.get().state()).isEqualTo(IntegrationState.FAILED);
        assertThat(sessions.get().configuration()).isNull();
    }
}
