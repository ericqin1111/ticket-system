package org.example.ticket.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.ticket.cache.config.CacheProperties;
import org.example.ticket.cache.model.CacheResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CaffeineCacheClient {

    private static final Object NULL_HOLDER = new Object();

    private final Cache<String, Object> cache;

    public CaffeineCacheClient(CacheProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getLocal().getMaximumSize())
                .expireAfterWrite(properties.getLocal().getExpireAfterWriteMs(), TimeUnit.MILLISECONDS)
                .recordStats()
                .build();
    }

    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> getIfPresent(String key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            return CacheResult.miss();
        }
        if (value == NULL_HOLDER) {
            return CacheResult.hit(null);
        }
        return CacheResult.hit((T) value);
    }

    public void put(String key, Object value) {
        cache.put(key, value == null ? NULL_HOLDER : value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
