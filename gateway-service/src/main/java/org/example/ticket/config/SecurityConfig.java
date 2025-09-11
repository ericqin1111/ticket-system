package org.example.ticket.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security 配置类，用于处理网关层面的安全问题
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                // 1. 禁用 CSRF (Cross-Site Request Forgery) 防护
                // 对于无状态的 JWT 认证 API 网关，CSRF 防护不是必需的
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // 2. 配置请求授权规则
                // 这里的配置是让所有请求都能通过 Spring Security 的安全验证层
                // 具体的认证逻辑由我们自定义的 AuthGlobalFilter 来处理
                .authorizeExchange(exchange -> exchange
                        .anyExchange().permitAll()
                );

        return http.build();
    }
}
