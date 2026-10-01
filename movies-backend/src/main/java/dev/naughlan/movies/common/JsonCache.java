package dev.naughlan.movies.common;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside on Redis, storing values as JSON:
 * read the cache; on a miss, load from the source of truth and store the result with a TTL.
 *
 * The cache is an optimisation, never a dependency: if Redis is slow or down, or holds something
 * unreadable, the value is simply loaded from the source. Misses (e.g. a 404 thrown by the loader)
 * are not cached.
 */
@Component
public class JsonCache {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final Duration ttl;
    // Hit ratio = hits / (hits + misses): the number that tells you whether the cache earns its keep
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public JsonCache(StringRedisTemplate redis, JsonMapper jsonMapper, MeterRegistry meters,
                     @Value("${app.cache.enabled}") boolean enabled,
                     @Value("${app.cache.ttl}") Duration ttl) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.ttl = ttl;
        this.hits = cacheCounter(meters, "hit");
        this.misses = cacheCounter(meters, "miss");
        this.errors = cacheCounter(meters, "error");
    }

    private static Counter cacheCounter(MeterRegistry meters, String result) {
        return Counter.builder("app.cache.requests")
                .description("Cache lookups by outcome")
                .tag("result", result)
                .register(meters);
    }

    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader) {
        return getOrLoad(key, json -> jsonMapper.readValue(json, type), loader);
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader) {
        return getOrLoad(key, json -> jsonMapper.readValue(json, type), loader);
    }

    /**
     * A counter to put in keys. Incrementing it (see {@link #nextGeneration}) makes every key built with
     * the old value unreachable at once, without scanning Redis; the orphans expire through their TTL.
     */
    public long generation(String namespace) {
        if (!enabled) {
            return 0;
        }
        try {
            String value = redis.opsForValue().get(namespace + ":generation");
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            log.debug("Cache unavailable reading generation of {}: {}", namespace, e.getMessage());
            return 0;
        }
    }

    public void nextGeneration(String namespace) {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().increment(namespace + ":generation");
        } catch (DataAccessException e) {
            // Entries still expire through their TTL, so stale data is bounded even if this fails
            log.warn("Could not invalidate cache namespace {}: {}", namespace, e.getMessage());
        }
    }

    private <T> T getOrLoad(String key, Function<String, T> parse, Supplier<T> loader) {
        if (!enabled) {
            return loader.get();
        }
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                T value = parse.apply(cached);
                hits.increment();
                return value;
            }
            misses.increment();
        } catch (DataAccessException e) {
            errors.increment();
            log.debug("Cache unavailable, reading {} from the source: {}", key, e.getMessage());
            return loader.get();
        } catch (JacksonException e) {
            misses.increment();
            // e.g. the response shape changed since this entry was written
            log.debug("Discarding unreadable cache entry {}: {}", key, e.getMessage());
        }

        T value = loader.get();
        try {
            redis.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (DataAccessException e) {
            log.debug("Could not cache {}: {}", key, e.getMessage());
        }
        return value;
    }
}
