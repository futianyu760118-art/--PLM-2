package com.hjgd.plm.auth.controller;

import com.hjgd.plm.auth.dto.LoginDTO;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.TokenBlacklistService;
import com.hjgd.plm.auth.util.JwtUtil;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.auth.dto.LoginVO;
import com.hjgd.plm.config.JwtProperties;
import com.hjgd.plm.system.entity.SysLoginLog;
import com.hjgd.plm.system.entity.SysUser;
import com.hjgd.plm.system.mapper.SysLoginLogMapper;
import com.hjgd.plm.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAND-151 / D2：登录（成功与失败）都必须落审计，且审计里绝不出现口令。
 */
@DisplayName("AuthController.login: D2 登录审计落库")
class AuthControllerLoginAuditTest {

    private static final String RAW_PASSWORD = "S3cret!Pass";

    private AuthenticationManager authenticationManager;
    private JwtUtil jwtUtil;
    private SysUserMapper sysUserMapper;
    private SysLoginLogMapper loginLogMapper;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtUtil = mock(JwtUtil.class);
        sysUserMapper = mock(SysUserMapper.class);
        loginLogMapper = mock(SysLoginLogMapper.class);
        controller = new AuthController(
                authenticationManager,
                jwtUtil,
                new JwtProperties(),
                sysUserMapper,
                loginLogMapper,
                mock(PasswordEncoder.class),
                mock(TokenBlacklistService.class));
    }

    private LoginDTO dto() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("zhangsan");
        dto.setPassword(RAW_PASSWORD);
        return dto;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "JUnit-UA/1.0");
        return request;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(11L);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setPassword("$2a$10$alreadyhashed");
        user.setAvatar("avatar.png");
        user.setStatus(1);
        user.setMustChangePassword(0);
        return user;
    }

    private SysLoginLog capturedLoginLog() {
        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 逐字段核对：审计记录的任何字段都不得出现口令明文 */
    private void assertNoPlaintextPassword(SysLoginLog entry) throws IllegalAccessException {
        for (Field field : SysLoginLog.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.get(entry) instanceof String value) {
                assertFalse(value.contains(RAW_PASSWORD),
                        "审计字段 " + field.getName() + " 出现口令明文: " + value);
            }
        }
    }

    @Test
    @DisplayName("登录成功：写一条 result=1、含用户ID/用户名/IP/UA 的审计，不含口令")
    void shouldAuditSuccessfulLogin() throws Exception {
        LoginUser principal = new LoginUser(user(), List.of("ADMIN"), List.of("user:list"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken(11L, "zhangsan", "张三", "ADMIN")).thenReturn("jwt-token");

        Result<LoginVO> result = controller.login(dto(), request());

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("jwt-token", result.getData().getToken());

        SysLoginLog entry = capturedLoginLog();
        assertEquals(SysLoginLog.RESULT_SUCCESS, entry.getResult());
        assertEquals("zhangsan", entry.getUsername());
        assertEquals(11L, entry.getUserId());
        assertEquals("10.1.2.3", entry.getIp());
        assertEquals("JUnit-UA/1.0", entry.getDevice());
        assertNoPlaintextPassword(entry);
    }

    @Test
    @DisplayName("登录失败：仍写一条 result=0 的审计，原异常照常抛出（不改变 401 口径）")
    void shouldAuditFailedLoginAndRethrow() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("用户名或密码错误"));

        assertThrows(AuthenticationException.class, () -> controller.login(dto(), request()));

        SysLoginLog entry = capturedLoginLog();
        assertEquals(SysLoginLog.RESULT_FAILURE, entry.getResult());
        assertEquals("zhangsan", entry.getUsername());
        assertNull(entry.getUserId(), "认证失败时无从得知账号，不得回填用户ID");
        assertEquals("10.1.2.3", entry.getIp());
        assertNoPlaintextPassword(entry);
    }

    @Test
    @DisplayName("审计落库失败不得影响登录主流程")
    void shouldNotBreakLoginWhenAuditFails() {
        LoginUser principal = new LoginUser(user(), List.of("ADMIN"), List.of());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken(11L, "zhangsan", "张三", "ADMIN")).thenReturn("jwt-token");
        when(loginLogMapper.insert(any())).thenThrow(new RuntimeException("audit db down"));

        Result<LoginVO> result = controller.login(dto(), request());

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("jwt-token", result.getData().getToken());
    }
}
