package com.prog2.catalog.infrastructure.config;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class CatalogOperationalPropertiesTests {
    private static final String PREFIX = "catalog.operational.";

    static MockEnvironment environment() throws IOException {
        var environment = new MockEnvironment();
        new YamlPropertySourceLoader().load("catalog-defaults", new ClassPathResource("application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return environment;
    }

    private static Arguments setting(String suffix, long expected,
            ToLongFunction<CatalogOperationalProperties> read) {
        return Arguments.of(suffix, expected, read);
    }

    static Stream<Arguments> settings() {
        return Stream.of(
                setting("integration.max-attempts", 3, p -> p.integration().maxAttempts()),
                setting("integration.retry-delay-ms", 5000, p -> p.integration().retryDelay().toMillis()),
                setting("integration.connect-timeout-ms", 5000, p -> p.integration().connectTimeout().toMillis()),
                setting("integration.read-timeout-ms", 5000, p -> p.integration().readTimeout().toMillis()),
                setting("integration.request-timeout-ms", 10000, p -> p.integration().requestTimeout().toMillis()),
                setting("snapshot.max-attempts", 3, p -> p.snapshot().maxAttempts()),
                setting("snapshot.retry-delay-ms", 5000, p -> p.snapshot().retryDelay().toMillis()),
                setting("snapshot.connect-timeout-ms", 5000, p -> p.snapshot().connectTimeout().toMillis()),
                setting("snapshot.read-timeout-ms", 10000, p -> p.snapshot().readTimeout().toMillis()),
                setting("snapshot.request-timeout-ms", 20000, p -> p.snapshot().requestTimeout().toMillis()),
                setting("incremental.max-attempts", 3, p -> p.incremental().maxAttempts()),
                setting("incremental.retry-delay-ms", 5000, p -> p.incremental().retryDelay().toMillis()),
                setting("incremental.retry-cooldown-ms", 30000, p -> p.incremental().retryCooldown().toMillis()),
                setting("recovery.max-cycles", 3, p -> p.recovery().maxCycles()),
                setting("recovery.cycle-delay-ms", 30000, p -> p.recovery().cycleDelay().toMillis()),
                setting("recovery.block-delay-ms", 300000, p -> p.recovery().blockDelay().toMillis()),
                setting("redis.connect-timeout-ms", 5000, p -> p.redis().connectTimeout().toMillis()),
                setting("redis.command-timeout-ms", 5000, p -> p.redis().commandTimeout().toMillis()),
                setting("redis.read-timeout-ms", 20000, p -> p.redis().readTimeout().toMillis()),
                setting("kafka.timeout-ms", 10000, p -> p.kafkaTimeout().toMillis()),
                setting("monitor.interval-ms", 30000, p -> p.monitorInterval().toMillis()));
    }

    @ParameterizedTest
    @MethodSource("settings")
    void preservesDefaultsAndResolvesEachOptionalEnvironmentOverride(String suffix, long expected,
            ToLongFunction<CatalogOperationalProperties> read) throws IOException {
        var environment = environment();
        var original = CatalogOperationalProperties.from(environment);
        assertThat(read.applyAsLong(original)).isEqualTo(expected);

        String variable = "CATALOG_" + suffix.replace('.', '_').replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        environment.setProperty(variable, "17");
        assertThat(read.applyAsLong(CatalogOperationalProperties.from(environment))).isEqualTo(17);
        // El bean anterior conserva su configuración; no hay recarga sobre el objeto compartido.
        assertThat(read.applyAsLong(original)).isEqualTo(expected);

        environment.setProperty(variable, String.valueOf(Integer.MAX_VALUE));
        assertThat(read.applyAsLong(CatalogOperationalProperties.from(environment))).isEqualTo(Integer.MAX_VALUE);
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of("integration.max-attempts", "redis.read-timeout-ms")
                .flatMap(key -> Stream.of("", " ", "0", "-1", "1.5", "fictional-sensitive-value", "2147483648")
                        .map(value -> Arguments.of(key, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidValuesWithoutEchoingThemOrRetainingTheConversionCause(String suffix, String value)
            throws IOException {
        var environment = environment().withProperty(PREFIX + suffix, value);
        assertThatExceptionOfType(BeanCreationException.class)
                .isThrownBy(() -> CatalogOperationalProperties.from(environment))
                .withMessageContaining(PREFIX + suffix)
                .satisfies(failure -> {
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).doesNotContain("fictional-sensitive-value");
                });
    }

    @Test
    void rejectsMissingDefaultsRatherThanConstructingAnUnlimitedOrPartialConfiguration() {
        var environment = new MockEnvironment();
        assertThatExceptionOfType(BeanCreationException.class)
                .isThrownBy(() -> CatalogOperationalProperties.from(environment))
                .withMessageContaining(PREFIX + "integration.max-attempts");
    }

    @Test
    void rejectsInvalidConfigurationBeforeCreatingResources() throws IOException {
        var environment = environment()
                .withProperty("CATEDRA_BASE_URL", "http://central.test")
                .withProperty("CATEDRA_USERNAME", "fictional-user")
                .withProperty("CATEDRA_PASSWORD", "fictional-password")
                .withProperty("CATEDRA_GROUP_ID", "fictional-group")
                .withProperty(PREFIX + "snapshot.request-timeout-ms", "fictional-sensitive-value");
        var created = new AtomicBoolean();
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            context.addBeanFactoryPostProcessor(CatalogConfiguration.requiredIntegrationSettings(environment));
            context.registerBean("externalResource", Object.class, () -> {
                created.set(true);
                return new Object();
            });
            assertThatExceptionOfType(BeanCreationException.class).isThrownBy(context::refresh)
                    .withMessageContaining(PREFIX + "snapshot.request-timeout-ms")
                    .withMessageNotContaining("fictional-sensitive-value");
            assertThat(created).isFalse();
        }
    }
}
