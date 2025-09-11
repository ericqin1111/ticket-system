package org.example.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey secretKey;

    /**
     * 在构造函数之后执行，用于初始化 SecretKey
     */
    @PostConstruct
    public void init() {
        // 将 Base64 编码的密钥字符串解码为字节数组
        byte[] keyBytes = Base64.getDecoder().decode(secretString);
        // 根据字节数组生成一个安全的 SecretKey
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        // 存放自定义数据，我们将 userId 放入 claim 中，以便网关解析
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims) // 设置自定义 claim
                .subject(username) // 设置主题，通常是用户名
                .issuedAt(new Date(System.currentTimeMillis())) // 设置签发时间
                .expiration(new Date(System.currentTimeMillis() + expiration)) // 设置过期时间
                .signWith(secretKey) // 使用密钥签名
                .compact(); // 构建并返回字符串
    }

    /**
     * 从 JWT 中解析 Claims
     * @param token JWT 字符串
     * @return Claims 对象
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey) // 使用相同的密钥进行验证
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 JWT 中获取用户ID
     * @param token JWT 字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        return getClaimsFromToken(token).get("userId", Long.class);
    }

    /**
     * 从 JWT 中获取用户名
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 验证 Token 是否过期
     * @param token JWT 字符串
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        Date expirationDate = getClaimsFromToken(token).getExpiration();
        return expirationDate.before(new Date());
    }

    /**
     * 验证 Token 是否有效 (签名是否正确 & 是否未过期)
     * @param token JWT 字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            // 只要能成功解析就说明签名是正确的
            getClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            // 解析失败（签名错误、格式错误等）或已过期
            return false;
        }
    }
}
