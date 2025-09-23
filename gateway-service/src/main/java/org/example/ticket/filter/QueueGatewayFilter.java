package org.example.ticket.filter;


import Constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.example.ticket.queue.strategy.QueueStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class QueueGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(QueueGatewayFilter.class);

    private final List<QueueStrategy> strategies;

    @Autowired
    public QueueGatewayFilter(List<QueueStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(QueueStrategy::getOrder))
                .collect(Collectors.toList());
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("============================QueueGatewayFilter start==================");
        for (QueueStrategy strategy : strategies) {
            log.info("strategy:{}",strategy);
            if (strategy.support(exchange)) {
                log.info("策略配置有效");
                return strategy.apply(exchange, chain);
            }
        }

        return chain.filter(exchange);
    }

//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
//
//
//    Logger log= LoggerFactory.getLogger(QueueGatewayFilter.class);
//
//    private static final AntPathMatcher MATCHER = new AntPathMatcher();
//
//    private static final String ORDER_CREATE_PATH="/api/orders/create";
//
//
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ServerHttpRequest request=exchange.getRequest();
//        ServerHttpResponse response= (ServerHttpResponse) exchange.getResponse();
//        String path=request.getURI().getPath();
//
//        log.info("我进入了{}",path);
//        if(!MATCHER.match(ORDER_CREATE_PATH,path)){
//            log.error("拦截链失效");
//            return chain.filter(exchange);
//        }
//
//        String userId=request.getHeaders().getFirst("X-User-ID");
//        String passToken=request.getHeaders().getFirst("X-Pass-Token");
//        String ticketItemId=request.getHeaders().getFirst("X-Ticket-Item-Id");
//
//        if(ticketItemId==null){
//            return chain.filter(exchange);
//        }
//        /**
//         * 检查是否需要排队
//         */
//
//        String configKey= String.format(RedisKeyConstants.QUEUE_CONFIG,ticketItemId);
//
//        String isActive= (String) stringRedisTemplate.opsForHash().get(configKey,"is_active");
//
//        if(! "1".equals(isActive)){
//            String rate= (String) stringRedisTemplate.opsForHash().get(configKey,"release_rate");
//            return chain.filter(exchange);
//        }
//
//        if(userId!=null  && passToken!=null){
//
//            String passkey= String.format(RedisKeyConstants.QUEUE_PASS,ticketItemId,userId);
//            String expectedToken=stringRedisTemplate.opsForValue().get(passkey);
//
//            if(passToken.equals(expectedToken)){
//
//                stringRedisTemplate.delete(passkey);
//                return chain.filter(exchange);
//            }
//        }
//
//
//
//
//        response.setStatusCode(HttpStatus.TOO_EARLY);
//        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");
//        String redirectingMessage = "{\"status\":\"QUEUED\",\"message\":\"请排队\",\"queue_enter_url\":\"/api/queue/enter\",\"queue_status_url\":\"/api/queue/status\"}";
//
//        byte[] body=redirectingMessage.getBytes(StandardCharsets.UTF_8);
//        DataBuffer buffer=response.bufferFactory().wrap(body);
//
//        return response.writeWith(Mono.just(buffer));
//    }

    @Override
    public int getOrder() {
        return -99;
    }


}
