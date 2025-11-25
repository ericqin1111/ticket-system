package org.example.ticket.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {

    private Key key = new Key();
    private Local local = new Local();
    private Remote redis = new Remote();
    private Breaker breaker = new Breaker();

    @Data
    public static class Key {
        /**
         * 版本号，用于全量失效。
         */
        private int tierVersion = 1;
    }

    @Data
    public static class Local {
        private boolean enabled = true;
        private long maximumSize = 50_000;
        private long expireAfterWriteMs = 20 * 60 * 1000;
        private long negativeTtlMs = 30 * 1000;
    }

    @Data
    public static class Remote {
        private long ttlMs = 60 * 60 * 1000;
        private long negativeTtlMs = 30 * 1000;
        /**
         * 热 key 的可选长 TTL。
         */
        private long hotKeyTtlMs = 3 * 60 * 60 * 1000;
        private boolean compress = false;
    }

    @Data
    public static class Breaker {
        private boolean degradeOnError = true;
        private boolean useRedisLockForPenalty = false;
    }
}
