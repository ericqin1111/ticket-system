package org.example.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<String, String> template) {
        // 创建一个固定间隔的重试策略：重试2次，总共执行3次，每次间隔2秒
        // 如果3次都失败，消息会被投递到死信队列 (Dead Letter Queue)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        FixedBackOff backOff = new FixedBackOff(2000L, 2); // 间隔2秒，最多重试2次

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
