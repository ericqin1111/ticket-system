package org.example.ticket.queue.strategy;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface QueueStrategy {
    boolean support(ServerWebExchange exchange);

    Mono<Void> apply(ServerWebExchange exchange, GatewayFilterChain chain);

    int getOrder();
}
