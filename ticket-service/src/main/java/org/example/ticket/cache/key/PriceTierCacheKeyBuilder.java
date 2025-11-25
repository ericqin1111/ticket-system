package org.example.ticket.cache.key;

import org.example.ticket.cache.config.CacheProperties;
import org.springframework.stereotype.Component;

@Component
public class PriceTierCacheKeyBuilder {

    private static final String TEMPLATE = "ticket:tier:%s:v%s";

    private final CacheProperties cacheProperties;

    public PriceTierCacheKeyBuilder(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    public String build(Long tierId) {
        return String.format(TEMPLATE, tierId, cacheProperties.getKey().getTierVersion());
    }
}
