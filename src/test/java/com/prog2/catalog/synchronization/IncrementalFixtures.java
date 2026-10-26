package com.prog2.catalog.synchronization;

import java.time.Duration;
import java.util.*;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.infrastructure.client.dto.CatalogSnapshotResponse;

/** Publicador ficticio de pruebas. No utiliza configuración ni credenciales centrales reales. */
final class IncrementalFixtures {
    static final String ACCESS = "fictional-redis-access";
    private static final JsonMapper JSON=JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private IncrementalFixtures() { }

    static LettuceConnectionFactory factory(GenericContainer<?> container) {
        var server=new RedisStandaloneConfiguration(container.getHost(),container.getMappedPort(6379));
        server.setUsername("default"); server.setPassword(ACCESS);
        var options=ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build()).build();
        var factory=new LettuceConnectionFactory(server,LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500)).clientOptions(options).build());
        factory.afterPropertiesSet();
        return factory;
    }

    static void publish(StringRedisTemplate redis,long version,long oldest) {
        var response=JSON.readValue(SnapshotFixtures.json(version).replace("\"firstName\":\"Fictional\"",
                "\"firstName\":\"Updated "+version+"\""),CatalogSnapshotResponse.class);
        redis.delete(List.of("catedra:sync:professional-categories","catedra:sync:professionals","catedra:sync:weekly-schedules"));
        for(var row:response.professionalCategories()) { redis.opsForHash().put("catedra:sync:professional-categories",row.id().toString(),JSON.writeValueAsString(row)); }
        for(var row:response.professionals()) { redis.opsForHash().put("catedra:sync:professionals",row.id().toString(),JSON.writeValueAsString(row)); }
        for(var row:response.weeklySchedules()) { redis.opsForHash().put("catedra:sync:weekly-schedules",row.id().toString(),JSON.writeValueAsString(row)); }
        for(long i=8;i<=version;i++) {
            redis.opsForValue().set("catedra:sync:changes:"+i,"""
                    {"version":%d,"changes":{"professionalCategories":[1,1],"professionals":[101],"weeklySchedules":[201]},"extra":true}
                    """.formatted(i));
        }
        redis.opsForHash().putAll("catedra:sync:metadata",Map.of("currentVersion",Long.toString(version),
                "oldestAvailableVersion",Long.toString(oldest),"publishedAt",SnapshotFixtures.TIME.toString()));
        redis.opsForValue().multiSet(Map.of("catedra:sync:current-version",Long.toString(version),
                "catedra:sync:oldest-available-version",Long.toString(oldest)));
    }
}
