package dev.naughlan.movies.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.json.JsonMapper;

class JsonCacheTest {

    record Greeting(String text) {
    }

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonCache cache;
    private final AtomicInteger loads = new AtomicInteger();

    JsonCacheTest() {
        when(redis.opsForValue()).thenReturn(values);
        cache = new JsonCache(redis, JsonMapper.builder().build(), true, Duration.ofMinutes(10));
    }

    private Greeting load() {
        loads.incrementAndGet();
        return new Greeting("from the database");
    }

    @Test
    void aHitIsServedWithoutLoading() {
        when(values.get("k")).thenReturn("{\"text\":\"from the cache\"}");

        assertThat(cache.getOrLoad("k", Greeting.class, this::load).text()).isEqualTo("from the cache");
        assertThat(loads).hasValue(0);
    }

    @Test
    void aMissLoadsAndStoresWithTheTtl() {
        assertThat(cache.getOrLoad("k", Greeting.class, this::load).text()).isEqualTo("from the database");

        verify(values).set(eq("k"), eq("{\"text\":\"from the database\"}"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void redisBeingDownFallsBackToTheSource() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(cache.getOrLoad("k", Greeting.class, this::load).text()).isEqualTo("from the database");
        assertThat(loads).hasValue(1);
    }

    @Test
    void anUnreadableEntryIsReplaced() {
        when(values.get("k")).thenReturn("{not json");

        assertThat(cache.getOrLoad("k", Greeting.class, this::load).text()).isEqualTo("from the database");
        verify(values).set(eq("k"), anyString(), any(Duration.class));
    }
}
