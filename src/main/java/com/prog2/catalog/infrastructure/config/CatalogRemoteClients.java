package com.prog2.catalog.infrastructure.config;

import java.time.*;
import java.util.*;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.*;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.CatalogConnectionSettings;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.infrastructure.messaging.CatalogUpdatedListener;

/** Composición diferida: crea recursos con la configuración adquirida y conserva su propiedad/cierre. */
public class CatalogRemoteClients implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRemoteClients.class);
    private final JsonMapper json;
    private final Clock clock;
    private final Duration redisConnectTimeout;
    private final Duration redisCommandTimeout;
    private final Duration kafkaTimeout;
    private StringRedisTemplate redis;
    private String namespace;
    private LettuceConnectionFactory redisFactory;
    private KafkaMessageListenerContainer<String, String> consumer;
    private boolean stopped;
    private boolean closed;

    public CatalogRemoteClients(JsonMapper json, Clock clock, Duration redisConnectTimeout,
            Duration redisCommandTimeout, Duration kafkaTimeout) {
        this.json = json;
        this.clock = clock;
        this.redisConnectTimeout = redisConnectTimeout;
        this.redisCommandTimeout = redisCommandTimeout;
        this.kafkaTimeout = kafkaTimeout;
    }

    /** Se llama después del acceso técnico y con copia confirmada, nunca durante construcción de beans. */
    public synchronized void start(CatalogConnectionSettings settings, UpdateCatalogUseCase updates) {
        if (closed || stopped) { return; }
        if (redis == null) {
            var configuration = new RedisStandaloneConfiguration(settings.redisHost(), settings.redisPort());
            configuration.setUsername(settings.redisUsername());
            configuration.setPassword(settings.redisPassword());
            var options = ClientOptions.builder().socketOptions(SocketOptions.builder()
                    .connectTimeout(redisConnectTimeout).build()).build();
            var client = LettuceClientConfiguration.builder().commandTimeout(redisCommandTimeout)
                    .clientOptions(options).shutdownTimeout(Duration.ofSeconds(1)).build();
            redisFactory = new LettuceConnectionFactory(configuration, client);
            redisFactory.setEagerInitialization(false);
            redisFactory.afterPropertiesSet();
            namespace = settings.redisReadNamespace();
            redis = new StringRedisTemplate(redisFactory);
        }
        if (consumer == null) {
            consumer = createConsumer(settings);
            consumer.getContainerProperties().setMessageListener(new CatalogUpdatedListener(updates, json, clock,
                    this::stopConsumer));
            try { consumer.start(); }
            catch (RuntimeException failure) {
                consumer.stop(() -> { });
                consumer = null;
                throw failure;
            }
        }
    }

    /** Publica el template ya configurado bajo el mismo monitor que protege su inicialización. */
    public synchronized StringRedisTemplate redis() {
        StringRedisTemplate value = redis;
        if (value == null) { throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_CLIENTS_NOT_READY"); }
        return value;
    }
    public synchronized String namespace() { return namespace; }

    /** Detención asíncrona válida también cuando la solicita el propio hilo consumidor. */
    public synchronized void stopConsumer() {
        stopped = true;
        if (consumer != null) { consumer.stop(() -> { }); }
    }

    private KafkaMessageListenerContainer<String, String> createConsumer(CatalogConnectionSettings settings) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.kafkaBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, settings.kafkaConsumerGroupId());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);
        properties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(kafkaTimeout.toMillis()));
        properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Math.toIntExact(kafkaTimeout.toMillis()));
        properties.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        properties.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 5000);
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        var containerProperties = new ContainerProperties(settings.kafkaCatalogTopic());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setAsyncAcks(false);
        containerProperties.setSyncCommits(true);
        containerProperties.setSyncCommitTimeout(kafkaTimeout);
        containerProperties.setPollTimeout(1000);
        var container = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<String, String>(properties), containerProperties);
        container.setBeanName("catalog-updates");
        container.setCommonErrorHandler(new CommonErrorHandler() {
            @Override public boolean isAckAfterHandle() { return false; }
            @Override public boolean handleOne(Exception error, ConsumerRecord<?, ?> message,
                    Consumer<?, ?> source, MessageListenerContainer owner) {
                LOGGER.error("CATALOG_LISTENER_UNEXPECTED_FAILURE");
                stopConsumer();
                return false;
            }
            @Override public void handleOtherException(Exception error, Consumer<?, ?> source,
                    MessageListenerContainer owner, boolean batch) {
                // Frontera del cliente: autenticación/autorización no son cortes temporales de transporte.
                if (error instanceof org.apache.kafka.common.errors.AuthenticationException
                        || error instanceof org.apache.kafka.common.errors.AuthorizationException) {
                    LOGGER.error("CATALOG_KAFKA_ACCESS_REJECTED");
                    stopConsumer();
                } else {
                    LOGGER.warn("CATALOG_KAFKA_TEMPORARILY_UNAVAILABLE");
                }
            }
        });
        return container;
    }

    @Override
    public void close() {
        KafkaMessageListenerContainer<String, String> kafka;
        LettuceConnectionFactory lettuce;
        synchronized (this) {
            closed = true;
            kafka = consumer;
            lettuce = redisFactory;
        }
        // No esperar al hilo consumidor manteniendo el monitor que usa su callback de parada.
        if (kafka != null) { kafka.stop(); }
        if (lettuce != null) { lettuce.destroy(); }
    }
}
