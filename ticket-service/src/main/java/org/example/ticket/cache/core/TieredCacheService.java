package org.example.ticket.cache.core;

import lombok.extern.slf4j.Slf4j;
import org.example.ticket.cache.config.CacheProperties;
import org.example.ticket.cache.local.CaffeineCacheClient;
import org.example.ticket.cache.model.CacheOptions;
import org.example.ticket.cache.model.CacheResult;
import org.example.ticket.cache.remote.RedisCacheClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TieredCacheService implements CacheTemplate {

    private final CaffeineCacheClient localCache;
    private final RedisCacheClient redisCache;
    private final CacheProperties cacheProperties;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public TieredCacheService(CaffeineCacheClient localCache, RedisCacheClient redisCache, CacheProperties cacheProperties) {
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public <T> T get(String key, CacheOptions options, CacheLoader<T> loader, Class<T> type) {
        CacheOptions opts = options != null ? options : CacheOptions.from(cacheProperties);

        // L1
        CacheResult<T> localHit = localCache.getIfPresent(key, type);
        if (localHit.isHit()) {
            return localHit.getValue();
        }

        // L2
        CacheResult<T> redisHit = redisCache.get(key, type);
        if (redisHit.isHit()) {
            T val = redisHit.getValue();
            localCache.put(key, val);
            return val;
        }

        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                // double check after acquiring lock
                CacheResult<T> secondLocalHit = localCache.getIfPresent(key, type);
                if (secondLocalHit.isHit()) {
                    return secondLocalHit.getValue();
                }
                CacheResult<T> secondRedisHit = redisCache.get(key, type);
                if (secondRedisHit.isHit()) {
                    T val = secondRedisHit.getValue();
                    localCache.put(key, val);
                    return val;
                }

                T loaded = loader.load();
                if (loaded == null) {
                    redisCache.put(key, null, opts.getNegativeTtlMs());
                    localCache.put(key, null);
                    return null;
                }

                redisCache.put(key, loaded, opts.getTtlMs());
                localCache.put(key, loaded);
                return loaded;
            } catch (Exception ex) {
                log.error("Cache load failed for key {}", key, ex);
                if (opts.isAllowDegrade()) {
                    return null;
                }
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
