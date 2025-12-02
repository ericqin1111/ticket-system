package org.example.ticket.cache.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.cache.model.CacheResult;
import org.example.ticket.cache.remote.RedisMetrics;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class RedisCacheClient {
    private static final String NULL_TOKEN = "__NULL__";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMetrics redisMetrics;

    public RedisCacheClient(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper, RedisMetrics redisMetrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisMetrics = redisMetrics;
    }

    public <T> CacheResult<T> get(String key, Class<T> type) {
        var sample = redisMetrics.start();
        String raw = stringRedisTemplate.opsForValue().get(key);
        redisMetrics.stopAndRecord(sample, key);
        if (raw == null) {
            return CacheResult.miss();
        }
        if (NULL_TOKEN.equals(raw)) {
            return CacheResult.hit(null);
        }
        try {
            return CacheResult.hit(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException e) {
            MDC.put("cache.layer", "REDIS");
            log.warn("Failed to deserialize redis cache for key {}", key, e);
            MDC.remove("cache.layer");
            stringRedisTemplate.delete(key);
            return CacheResult.miss();
        }
    }

    public void put(String key, Object value, long ttlMs) {
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, NULL_TOKEN, Duration.ofMillis(ttlMs));
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, payload, Duration.ofMillis(ttlMs));
        } catch (JsonProcessingException e) {
            MDC.put("cache.layer", "REDIS");
            log.error("Failed to serialize value to redis for key {}", key, e);
            MDC.remove("cache.layer");
        }
    }

    public void invalidate(String key) {
        stringRedisTemplate.delete(key);
    }
}
