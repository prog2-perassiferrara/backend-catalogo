package com.prog2.catalog.synchronization.infrastructure.redis.adapter;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.infrastructure.config.CatalogJsonMapperFactory;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogChangesGateway;
import com.prog2.catalog.synchronization.infrastructure.client.dto.*;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;
import com.prog2.catalog.synchronization.infrastructure.redis.dto.CatalogChangeSetResponse;

/**
 * Adapter Redis síncrono. Dos lecturas de publicación detectan cambios bajo el supuesto central.
 * Un executor de lectura permite limitar la solicitud completa, no solo cada comando de red.
 * Su tarea solo lee y construye valores; nunca escribe en MySQL después de un timeout.
 */
public class RedisCatalogChangesAdapter implements CatalogChangesGateway, AutoCloseable {
    private final Supplier<StringRedisTemplate> templates;
    private final Supplier<String> namespaces;
    private final JsonMapper json;
    private final CatalogSnapshotResponseMapper entities;
    private final ExecutorService reader;
    private final Duration timeout;
    private final int maxBytes;

    public RedisCatalogChangesAdapter(Supplier<StringRedisTemplate> templates, Supplier<String> namespaces,
            JsonMapper json, CatalogSnapshotResponseMapper entities, ExecutorService reader,
            Duration timeout, int maxBytes) {
        this.templates = templates;
        this.namespaces = namespaces;
        this.json = CatalogJsonMapperFactory.strict(json);
        this.entities = entities;
        this.reader = reader;
        this.timeout = timeout;
        this.maxBytes = maxBytes;
    }

    @Override
    public CatalogChangesBatch readSince(long baseVersion) {
        Future<CatalogChangesBatch> task = reader.submit(() -> read(baseVersion));
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException _) {
            task.cancel(true);
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_REDIS_READ_TIMEOUT");
        } catch (InterruptedException _) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_READ_INTERRUPTED");
        } catch (ExecutionException failure) {
            // Clasificación en la frontera del executor, no selección de recursos de negocio.
            if (failure.getCause() instanceof CatalogDiscontinuityException discontinuity) { throw discontinuity; }
            if (failure.getCause() instanceof CatalogSnapshotException storage) { throw storage; }
            throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "CATALOG_REDIS_FAILURE");
        }
    }

    private CatalogChangesBatch read(long baseVersion) {
        try {
            StringRedisTemplate template = templates.get();
            String prefix = namespaces.get().replaceFirst(":\\*$", "").replaceFirst(":$", "");
            Budget budget = new Budget(maxBytes);
            CatalogPublication before = publication(template, prefix, budget);
            if (baseVersion < before.oldestAvailableVersion() || baseVersion > before.currentVersion()) {
                throw discontinuity(CatalogRecoveryReason.HISTORY_UNAVAILABLE);
            }
            Set<Long> categories = new LinkedHashSet<>();
            Set<Long> professionals = new LinkedHashSet<>();
            Set<Long> schedules = new LinkedHashSet<>();
            for (long version = baseVersion; version < before.currentVersion(); version++) {
                long requestedVersion = version + 1;
                checkInterruption();
                String body = template.opsForValue().get(prefix + ":changes:" + requestedVersion);
                if (body == null) { throw discontinuity(CatalogRecoveryReason.CHANGE_MISSING); }
                budget.consume(body);
                var change = parse(() -> json.readValue(body, CatalogChangeSetResponse.class));
                if (change == null || change.version() == null || change.version() != requestedVersion || change.changes() == null) {
                    throw discontinuity(CatalogRecoveryReason.INVALID_REMOTE_DATA);
                }
                addIds(categories, change.changes().professionalCategories());
                addIds(professionals, change.changes().professionals());
                addIds(schedules, change.changes().weeklySchedules());
            }
            var categoryValues = load(template, prefix + ":professional-categories", categories, budget,
                    body -> entities.category(json.readValue(body, ProfessionalCategoryResponse.class)), ProfessionalCategory::id);
            var professionalValues = load(template, prefix + ":professionals", professionals, budget,
                    body -> entities.professional(json.readValue(body, ProfessionalResponse.class)), Professional::id);
            var scheduleValues = load(template, prefix + ":weekly-schedules", schedules, budget,
                    body -> entities.schedule(json.readValue(body, WeeklyScheduleResponse.class)), WeeklySchedule::id);
            if (!before.equals(publication(template, prefix, budget))) {
                throw discontinuity(CatalogRecoveryReason.PUBLICATION_CHANGED);
            }
            var batch = new CatalogChangesBatch(baseVersion, before, categoryValues, professionalValues, scheduleValues);
            batch.validate();
            return batch;
        } catch (RedisConnectionFailureException failure) {
            throw new CatalogSnapshotException(accessDenied(failure) ? SnapshotFailureKind.TERMINAL
                    : SnapshotFailureKind.TEMPORARY, "CATALOG_REDIS_CONNECTION_UNAVAILABLE");
        } catch (TransientDataAccessException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "CATALOG_REDIS_TEMPORARY");
        } catch (DataAccessException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "CATALOG_REDIS_COMMAND_REJECTED");
        }
    }

    private CatalogPublication publication(StringRedisTemplate template, String prefix, Budget budget) {
        checkInterruption();
        List<String> fields = template.<String, String>opsForHash().multiGet(prefix + ":metadata",
                List.of("currentVersion", "oldestAvailableVersion", "publishedAt"));
        List<String> versions = template.opsForValue().multiGet(
                List.of(prefix + ":current-version", prefix + ":oldest-available-version"));
        return parse(() -> {
            fields.forEach(budget::consume);
            if (versions == null || !Objects.equals(fields.get(0), versions.get(0))
                    || !Objects.equals(fields.get(1), versions.get(1))) {
                throw discontinuity(CatalogRecoveryReason.PUBLICATION_CHANGED);
            }
            return new CatalogPublication(Long.parseLong(fields.get(0)), Long.parseLong(fields.get(1)),
                    Instant.parse(fields.get(2)));
        });
    }

    private <T> List<T> load(StringRedisTemplate template, String key, Set<Long> ids, Budget budget,
            Function<String, T> convert, ToLongFunction<T> identity) {
        List<T> result = new ArrayList<>();
        List<Long> ordered = new ArrayList<>(ids);
        for (int index = 0; index < ordered.size(); index += 128) {
            checkInterruption();
            List<Long> part = ordered.subList(index, Math.min(index + 128, ordered.size()));
            List<String> values = template.<String, String>opsForHash().multiGet(key, part.stream().map(Object::toString).toList());
            for (int i = 0; i < part.size(); i++) {
                String body = values.get(i);
                budget.consume(body);
                T value = parse(() -> convert.apply(body));
                if (identity.applyAsLong(value) != part.get(i)) { throw discontinuity(CatalogRecoveryReason.INVALID_REMOTE_DATA); }
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static void addIds(Set<Long> target, List<Long> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw discontinuity(CatalogRecoveryReason.INVALID_REMOTE_DATA);
        }
        target.addAll(values);
    }

    private static <T> T parse(Supplier<T> operation) {
        try { return operation.get(); }
        catch (CatalogDiscontinuityException failure) { throw failure; }
        catch (RuntimeException _) { throw discontinuity(CatalogRecoveryReason.INVALID_REMOTE_DATA); }
    }

    private static void checkInterruption() {
        if (Thread.currentThread().isInterrupted()) { throw discontinuity(CatalogRecoveryReason.READ_LIMIT_EXCEEDED); }
    }

    private static boolean accessDenied(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.startsWith("WRONGPASS") || message.startsWith("NOAUTH")
                    || message.startsWith("NOPERM"))) { return true; }
        }
        return false;
    }

    private static CatalogDiscontinuityException discontinuity(CatalogRecoveryReason reason) {
        return new CatalogDiscontinuityException(reason);
    }

    @Override public void close() { reader.shutdownNow(); }

    private static final class Budget {
        private long remaining;
        private Budget(long bytes) { remaining = bytes; }
        private void consume(String value) {
            if (value == null) { throw discontinuity(CatalogRecoveryReason.INVALID_REMOTE_DATA); }
            remaining -= value.getBytes(StandardCharsets.UTF_8).length;
            if (remaining < 0) { throw discontinuity(CatalogRecoveryReason.READ_LIMIT_EXCEEDED); }
        }
    }
}
