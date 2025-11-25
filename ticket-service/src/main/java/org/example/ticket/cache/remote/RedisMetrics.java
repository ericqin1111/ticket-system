package org.example.ticket.cache.remote;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RedisMetrics {
    private final MeterRegistry meterRegistry;
    private final Timer redisGetTimer;
    private static final Duration REDIS_SLOW_THRESHOLD = Duration.ofMillis(10);

    public RedisMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redisGetTimer = Timer.builder("cache.redis.get.duration")
                .description("Redis get duration for cache layer")
                .register(meterRegistry);
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public long stopAndRecord(Timer.Sample sample, String key) {
        long nanos = sample.stop(redisGetTimer);
        long elapsedMs = nanos / 1_000_000;
        if (elapsedMs > REDIS_SLOW_THRESHOLD.toMillis()) {
            meterRegistry.counter("cache.redis.slow.count").increment();
        }
        return elapsedMs;
    }
}
