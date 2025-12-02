package org.example.ticket.cache.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.cache.config.CacheProperties;
import org.example.ticket.cache.local.CaffeineCacheClient;
import org.example.ticket.cache.model.CacheOptions;
import org.example.ticket.cache.model.CacheResult;
import org.example.ticket.cache.remote.RedisCacheClient;
import org.example.ticket.cache.remote.RedisMetrics;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TieredCacheService implements CacheTemplate {

    private final CaffeineCacheClient localCache;
    private final RedisCacheClient redisCache;
    private final CacheProperties cacheProperties;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final Timer loadTimer;
    private static final Duration LOAD_SLOW_THRESHOLD = Duration.ofMillis(50);
    private final RedisMetrics redisMetrics;

    public TieredCacheService(
            CaffeineCacheClient localCache,
            RedisCacheClient redisCache,
            CacheProperties cacheProperties,
            MeterRegistry meterRegistry,
            RedisMetrics redisMetrics
    ) {
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.cacheProperties = cacheProperties;
        this.meterRegistry = meterRegistry;
        this.loadTimer = Timer.builder("cache.load.duration")
                .description("DB load duration when both caches miss")
                .register(meterRegistry);
        this.redisMetrics = redisMetrics;
    }

    @Override
    public <T> T get(String key, CacheOptions options, CacheLoader<T> loader, Class<T> type) {
        CacheOptions opts = options != null ? options : CacheOptions.from(cacheProperties);

        // L1
        CacheResult<T> localHit = localCache.getIfPresent(key, type);
        if (localHit.isHit()) {
            meterRegistry.counter("cache.hit", "layer", CacheLayer.LOCAL.name()).increment();
            return localHit.getValue();
        }

        // L2
        CacheResult<T> redisHit = redisCache.get(key, type);
        if (redisHit.isHit()) {
            meterRegistry.counter("cache.hit", "layer", CacheLayer.REDIS.name()).increment();
            T val = redisHit.getValue();
            localCache.put(key, val);
            return val;
        }
        meterRegistry.counter("cache.miss", "layer", "both").increment();

        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                // double check after acquiring lock
                CacheResult<T> secondLocalHit = localCache.getIfPresent(key, type);
                if (secondLocalHit.isHit()) {
                    meterRegistry.counter("cache.hit", "layer", CacheLayer.LOCAL.name()).increment();
                    return secondLocalHit.getValue();
                }
                CacheResult<T> secondRedisHit = redisCache.get(key, type);
                if (secondRedisHit.isHit()) {
                    meterRegistry.counter("cache.hit", "layer", CacheLayer.REDIS.name()).increment();
                    T val = secondRedisHit.getValue();
                    localCache.put(key, val);
                    return val;
                }

                Timer.Sample sample = Timer.start();
                T loaded = loader.load();
                long elapsedMs = sample.stop(loadTimer) / 1_000_000;
                if (elapsedMs > LOAD_SLOW_THRESHOLD.toMillis()) {
                    MDC.put("cache.layer", CacheLayer.DB.name());
                    log.warn("DB load slow, key={}, took={}ms", key, elapsedMs);
                    MDC.remove("cache.layer");
                }
                if (loaded == null) {
                    redisCache.put(key, null, opts.getNegativeTtlMs());
                    localCache.put(key, null);
                    return null;
                }

                redisCache.put(key, loaded, opts.getTtlMs());
                localCache.put(key, loaded);
                return loaded;
            } catch (Exception ex) {
                meterRegistry.counter("cache.load.fail").increment();
                MDC.put("cache.key", key);
                MDC.put("cache.layer", CacheLayer.DB.name());
                log.error("Cache load failed for key {}", key, ex);
                if (opts.isAllowDegrade()) {
                    MDC.remove("cache.key");
                    MDC.remove("cache.layer");
                    return null;
                }
                MDC.remove("cache.key");
                MDC.remove("cache.layer");
                throw new RuntimeException(ex);
            } finally {
                locks.remove(key);
            }
        }
    }

    @Override
    public void invalidate(String key) {
        redisCache.invalidate(key);
        localCache.invalidate(key);
    }
}
