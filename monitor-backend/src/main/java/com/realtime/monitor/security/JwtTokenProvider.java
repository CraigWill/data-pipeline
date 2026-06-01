package com.realtime.monitor.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT Token 提供者
 * 
 * ⚠️ 安全提示：请将 jwt.secret 配置项设置为从环境变量读取的强密钥
 * 生成命令：openssl rand -base64 32
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 小时
    private long jwtExpiration;

    /**
     * 安全修复：验证 JWT Secret 强度
     * - 密钥长度至少 32 字符（256 位）
     * - 防止使用弱密钥导致 token 被伪造
     */
    @PostConstruct
    public void validateJwtSecret() {
        if (jwtSecret == null) {
            throw new IllegalStateException(
                "JWT_SECRET 未设置！请在环境变量中配置 JWT_SECRET。\n" +
                "生成密钥：openssl rand -base64 64"
            );
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET 长度不足！必须至少 32 字符（当前：" + jwtSecret.length() + "）。\n" +
                "生成密钥：openssl rand -base64 64"
            );
        }
        log.info("JWT Secret 验证通过，长度：{} 字符", jwtSecret.length());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }
}
