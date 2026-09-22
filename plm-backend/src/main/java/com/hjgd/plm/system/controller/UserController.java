package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysUser;
import com.hjgd.plm.system.mapper.SysRoleMapper;
import com.hjgd.plm.system.mapper.SysUserMapper;
import com.hjgd.plm.system.entity.SysRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "用户分页")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "新增用户")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode((String) body.getOrDefault("password", "123456")));
        user.setRealName((String) body.get("realName"));
        user.setEmployeeNo((String) body.get("employeeNo"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        user.setDeptId(body.get("deptId") == null ? null : Long.valueOf(body.get("deptId").toString()));
        user.setStatus(1);
        userMapper.insert(user);
        List<String> roleCodes = (List<String>) body.get("roleCodes");
        if (roleCodes != null) {
            assignRoles(user.getId(), roleCodes);
        }
        return Result.success(user);
    }

    @Operation(summary = "修改用户")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public Result<SysUser> update(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setRealName((String) body.get("realName"));
        user.setEmployeeNo((String) body.get("employeeNo"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        user.setDeptId(body.get("deptId") == null ? null : Long.valueOf(body.get("deptId").toString()));
        user.setStatus(body.get("status") == null ? 1 : Integer.valueOf(body.get("status").toString()));
        userMapper.updateById(user);
        List<String> roleCodes = (List<String>) body.get("roleCodes");
        if (roleCodes != null) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
            assignRoles(id, roleCodes);
        }
        return Result.success(user);
    }

    @Operation(summary = "重置密码")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        SysUser user = userMapper.selectById(id);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        return Result.success();
    }

    @Operation(summary = "所有角色(下拉用)")
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.success(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1)));
    }

    private void assignRoles(Long userId, List<String> roleCodes) {
        for (String code : roleCodes) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code));
            if (role != null) {
                jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?, ?) ON CONFLICT DO NOTHING",
                        userId, role.getId());
            }
        }
    }
}
