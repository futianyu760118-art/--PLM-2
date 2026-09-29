package com.hjgd.plm.auth.controller;

import com.hjgd.plm.auth.dto.ChangePasswordDTO;
import com.hjgd.plm.auth.dto.LoginDTO;
import com.hjgd.plm.auth.dto.LoginVO;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.NodeScryptPasswordEncoder;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.auth.security.TokenBlacklistService;
import com.hjgd.plm.auth.util.JwtUtil;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.config.JwtProperties;
import com.hjgd.plm.system.entity.SysUser;
import com.hjgd.plm.system.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtUtil.generateToken(
                loginUser.getUserId(),
                loginUser.getUsername(),
                loginUser.getRealName(),
                loginUser.getPrimaryRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenHeader(jwtProperties.getHeader());
        vo.setTokenPrefix(jwtProperties.getPrefix());
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setRealName(loginUser.getRealName());
        vo.setAvatar(loginUser.getUser().getAvatar());
        vo.setRoles(loginUser.getRoles());
        vo.setPermissions(loginUser.getPermissions());
        // 须在透明重哈希清标记之前取值
        vo.setMustChangePassword(loginUser.isMustChangePassword());

        // R5: 登录成功留痕（AC6.6）
        try {
            sysUserMapper.updateLoginInfo(loginUser.getUserId(), resolveClientIp(request));
        } catch (Exception e) {
            log.warn("更新登录信息失败 userId={}", loginUser.getUserId(), e);
        }
        // R6: EBMS 遗留 scrypt 口令透明重哈希为 BCrypt（ADR-2）
        transparentRehash(loginUser, dto.getPassword());

        log.info("用户[{}]登录成功", loginUser.getUsername());
        return Result.success(vo);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<LoginVO> info() {
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        LoginVO vo = new LoginVO();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setRealName(loginUser.getRealName());
        vo.setAvatar(loginUser.getUser().getAvatar());
        vo.setRoles(loginUser.getRoles());
        vo.setPermissions(loginUser.getPermissions());
        vo.setMustChangePassword(loginUser.isMustChangePassword());
        return Result.success(vo);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // R2: 无状态 JWT 需显式拉黑，否则登出后原令牌仍可用（AC6.5）
        try {
            LoginUser loginUser = SecurityUtils.getCurrentUser();
            String token = loginUser.getToken();
            if (token != null) {
                tokenBlacklistService.revoke(jwtUtil.getJti(token), jwtUtil.getExpirationMillis(token));
            }
        } catch (Exception e) {
            log.warn("登出拉黑令牌失败", e);
        }
        SecurityContextHolder.clearContext();
        return Result.success();
    }

    @Operation(summary = "自助修改密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String stored = (user.getLegacyPassword() != null && !user.getLegacyPassword().isEmpty())
                ? user.getLegacyPassword() : user.getPassword();
        if (!passwordEncoder.matches(dto.getOldPassword(), stored)) {
            throw new BusinessException("原密码不正确");
        }
        sysUserMapper.updatePasswordClearLegacy(user.getId(), passwordEncoder.encode(dto.getNewPassword()));
        log.info("用户[{}]修改密码成功", loginUser.getUsername());
        return Result.success();
    }

    /**
     * 登录成功且账号仍持有 EBMS scrypt 遗留口令时，就地重哈希为 BCrypt 并清空遗留列。
     * 失败不影响本次登录（用户仍可下次登录再试），仅记警告，不输出任何口令材料。
     */
    private void transparentRehash(LoginUser loginUser, String rawPassword) {
        SysUser user = loginUser.getUser();
        if (!NodeScryptPasswordEncoder.isScrypt(user.getLegacyPassword())) {
            return;
        }
        try {
            sysUserMapper.updatePasswordClearLegacy(loginUser.getUserId(), passwordEncoder.encode(rawPassword));
            user.setLegacyPassword(null);
            log.info("用户[{}] 遗留 scrypt 口令已透明重哈希为 BCrypt", loginUser.getUsername());
        } catch (Exception e) {
            log.warn("透明重哈希失败 userId={}", loginUser.getUserId(), e);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip == null) {
            return "";
        }
        return ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
