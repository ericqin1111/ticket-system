package org.example.ticket.cache.core;

import org.example.ticket.cache.model.CacheOptions;

public interface CacheTemplate {
    <T> T get(String key, CacheOptions options, CacheLoader<T> loader, Class<T> type);

    void invalidate(String key);
}
