package com.hjgd.plm.auth;

import com.hjgd.plm.auth.util.JwtUtil;
import com.hjgd.plm.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类测试
 */
@DisplayName("JWT认证测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("TestSecretKeyForJwtTokenSigningMustBeLongEnoughForHS512Algorithm2026");
        props.setExpire(3600L);
        props.setHeader("Authorization");
        props.setPrefix("Bearer ");
        jwtUtil = new JwtUtil(props);
    }

    @Test
    @DisplayName("生成Token并解析")
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken(1L, "admin", "管理员", "ADMIN");

        assertNotNull(token);
        Claims claims = jwtUtil.parseToken(token);
        assertEquals(1L, ((Number) claims.get("userId")).longValue());
        assertEquals("admin", claims.get("username"));
        assertEquals("管理员", claims.get("realName"));
        assertEquals("ADMIN", claims.get("roleCode"));
    }

    @Test
    @DisplayName("有效Token验证通过")
    void shouldValidateValidToken() {
        String token = jwtUtil.generateToken(1L, "admin", "管理员", "ADMIN");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("无效Token验证失败")
    void shouldRejectInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("从Token提取用户信息")
    void shouldExtractUserInfoFromToken() {
        String token = jwtUtil.generateToken(1L, "admin", "管理员", "ADMIN");
        assertEquals("admin", jwtUtil.getUsernameFromToken(token));
        assertEquals(1L, jwtUtil.getUserIdFromToken(token));
        assertEquals("ADMIN", jwtUtil.getRoleFromToken(token));
    }
}
