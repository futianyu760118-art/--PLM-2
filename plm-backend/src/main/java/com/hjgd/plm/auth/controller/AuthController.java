package com.hjgd.plm.auth.controller;

import com.hjgd.plm.auth.dto.LoginDTO;
import com.hjgd.plm.auth.dto.LoginVO;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.auth.util.JwtUtil;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.config.JwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
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
        return Result.success(vo);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.success();
    }
}
