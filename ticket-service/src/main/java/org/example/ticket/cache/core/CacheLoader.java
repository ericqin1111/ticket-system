package org.example.ticket.cache.core;

@FunctionalInterface
public interface CacheLoader<T> {
    T load() throws Exception;
}
