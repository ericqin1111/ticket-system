package org.example.ticket.cache.model;

import lombok.Value;

@Value
public class CacheResult<T> {
    boolean hit;
    T value;

    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(false, null);
    }

    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(true, value);
    }
}
