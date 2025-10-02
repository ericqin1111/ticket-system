package org.example.gateway.filter;



import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.example.gateway.config.AuthProperties;
import org.example.gateway.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Resource
    private AuthProperties authProperties;

    @Resource
    private JwtUtil jwtUtil;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单校验，如果匹配白名单，则直接放行
        for (String skipUrl : authProperties.getSkipUrls()) {
            if (antPathMatcher.match(skipUrl, path)) {
                log.info("Path {} is in whitelist, skipping auth.", path);
                return chain.filter(exchange);
            }
        }

        // 2. 获取请求头中的 Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 3. 校验 Authorization 头是否存在且格式正确 (Bearer token)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authorization header is missing or invalid for path: {}", path);
            return unauthorizedResponse(exchange, "Authorization header is missing or invalid.");
        }

        // 4. 提取 JWT
        String token = authHeader.substring(7); // "Bearer ".length() = 7

        // 5. 验证 JWT 的有效性
        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return unauthorizedResponse(exchange, "Invalid or expired token.");
        }

        // 6. 从 JWT 中解析用户信息
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        // 7. 将用户信息添加到请求头中，传递给下游服务
        ServerHttpRequest newRequest = request.mutate()
                .header("X-User-ID", String.valueOf(userId))
                .header("X-User-Name", username)
                .build();

        // 8. 创建新的 exchange 并传递给下一个过滤器
        ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
        log.info("User {} (ID: {}) authenticated successfully. Forwarding request to {}.", username, userId, path);
        return chain.filter(newExchange);
    }

    /**
     * 设置 401 Unauthorized 响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        // 可以根据需要返回更详细的 JSON 错误信息
        String responseBody = "{\"code\": 401, \"message\": \"" + message + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(responseBody.getBytes())));
    }

    /**
     * 设置过滤器的执行顺序，数值越小，优先级越高
     * 我们需要它在路由过滤器之前执行
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
