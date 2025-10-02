package org.example.ticket.config;

import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

@TestConfiguration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    public EmbeddedRedisConfig() throws IOException {
        // 使用一个随机的、非6379的端口，避免与本地正在运行的Redis冲突
        this.redisServer = RedisServer.builder()
                .port(63790)
                .setting("maxmemory 128M") // 可选：为测试redis设置最大内存
                .build();
    }

    @PostConstruct
    public void postConstruct() {
        if (redisServer != null && !redisServer.isActive()) {
            redisServer.start();
        }
    }

    @PreDestroy
    public void preDestroy() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}