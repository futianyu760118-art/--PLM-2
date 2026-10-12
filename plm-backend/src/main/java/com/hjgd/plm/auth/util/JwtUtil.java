package com.hjgd.plm.auth.util;

import com.hjgd.plm.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, String realName, String roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("realName", realName);
        claims.put("roleCode", roleCode);
        long expireMs = properties.getExpire() * 1000L;
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                // jti: 登出黑名单以它为准（R2）
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    /** 令牌唯一标识，登出黑名单键 */
    public String getJti(String token) {
        return parseToken(token).getId();
    }

    /** 令牌过期时刻（epoch millis），黑名单据此回收 */
    public long getExpirationMillis(String token) {
        Date exp = parseToken(token).getExpiration();
        return exp == null ? 0L : exp.getTime();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    public Long getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("roleCode", String.class);
    }
}
