package org.example.ticket.cache.model;

import lombok.Builder;
import lombok.Value;
import org.example.ticket.cache.config.CacheProperties;

@Value
@Builder
public class CacheOptions {
    long ttlMs;
    long localTtlMs;
    long negativeTtlMs;
    boolean allowDegrade;

    public static CacheOptions from(CacheProperties properties) {
        return CacheOptions.builder()
                .ttlMs(properties.getRedis().getTtlMs())
                .localTtlMs(properties.getLocal().getExpireAfterWriteMs())
                .negativeTtlMs(properties.getLocal().getNegativeTtlMs())
                .allowDegrade(properties.getBreaker().isDegradeOnError())
                .build();
    }
}
