package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.system.entity.SysPermission;
import com.hjgd.plm.system.entity.SysRole;
import com.hjgd.plm.system.mapper.SysPermissionMapper;
import com.hjgd.plm.system.mapper.SysRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "角色权限管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "角色列表")
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getRoleLevel)));
    }

    @Operation(summary = "新增角色")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysRole> create(@RequestBody SysRole role) {
        roleMapper.insert(role);
        return Result.success(role);
    }

    @Operation(summary = "修改角色")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public Result<SysRole> update(@RequestBody SysRole role) {
        roleMapper.updateById(role);
        return Result.success(role);
    }

    @Operation(summary = "删除角色(内置角色禁止删除)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getBuiltin() != null && role.getBuiltin() == 1) {
            return Result.failed("内置角色禁止删除");
        }
        // D1: 角色为逻辑删除，数据库的 ON DELETE CASCADE 不会触发，
        // 残留的 sys_role_permission / sys_user_role 会让已删角色继续授权，故在此物理清理。
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", id);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", id);
        roleMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "全部权限(树形)")
    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions() {
        return Result.success(permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder)));
    }

    @Operation(summary = "查询角色已分配权限")
    @GetMapping("/{roleId}/permissions")
    public Result<List<Long>> rolePermissions(@PathVariable Long roleId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ?", Long.class, roleId);
        return Result.success(ids);
    }

    @Operation(summary = "分配权限")
    @PreAuthorize("hasRole('ADMIN')")
    // D3: 授权变更必须留痕（操作人/类型/目标/时间）
    @OperationLog(value = "角色授权变更")
    @PutMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        List<Long> permissionIds = body.get("permissionIds");
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) VALUES(?, ?) ON CONFLICT DO NOTHING",
                        roleId, pid);
            }
        }
        return Result.success();
    }
}
