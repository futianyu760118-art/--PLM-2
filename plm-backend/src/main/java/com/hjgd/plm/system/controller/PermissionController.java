package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysPermission;
import com.hjgd.plm.system.mapper.SysPermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限/菜单维护（R1 / F4）。
 *
 * <p>此前权限项仅能由 SQL 种子写入，菜单种子 {@code system:permission} 指向的页面并不存在——
 * 本控制器补齐维护能力。删除依赖 {@code sys_role_permission} 的外键 ON DELETE CASCADE 清理授权关系。
 */
@Tag(name = "权限菜单管理")
@RestController
@RequestMapping("/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionMapper permissionMapper;

    @Operation(summary = "权限树")
    @GetMapping("/tree")
    public Result<List<SysPermission>> tree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSortOrder)
                        .orderByAsc(SysPermission::getId));
        Map<Long, SysPermission> byId = new LinkedHashMap<>();
        // 浅拷贝，避免把 children 写回 MyBatis 缓存/实体语义
        for (SysPermission p : all) {
            SysPermission node = new SysPermission();
            node.setId(p.getId());
            node.setParentId(p.getParentId());
            node.setPermCode(p.getPermCode());
            node.setPermName(p.getPermName());
            node.setPermType(p.getPermType());
            node.setPath(p.getPath());
            node.setComponent(p.getComponent());
            node.setIcon(p.getIcon());
            node.setSortOrder(p.getSortOrder());
            node.setVisible(p.getVisible());
            node.setStatus(p.getStatus());
            node.setCreatedAt(p.getCreatedAt());
            node.setChildren(new ArrayList<>());
            byId.put(node.getId(), node);
        }
        List<SysPermission> roots = new ArrayList<>();
        for (SysPermission node : byId.values()) {
            Long parentId = node.getParentId();
            SysPermission parent = (parentId == null || parentId == 0L) ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return Result.success(roots);
    }

    @Operation(summary = "新增权限项")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysPermission> create(@RequestBody SysPermission permission) {
        validate(permission);
        if (existsCode(permission.getPermCode(), null)) {
            throw new BusinessException("权限编码已存在");
        }
        checkParent(permission.getParentId());
        permission.setId(null);
        permission.setCreatedAt(LocalDateTime.now());
        applyDefaults(permission);
        permissionMapper.insert(permission);
        return Result.success(permission);
    }

    @Operation(summary = "修改权限项")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<SysPermission> update(@PathVariable Long id, @RequestBody SysPermission permission) {
        SysPermission existing = permissionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("权限项不存在");
        }
        validate(permission);
        if (existsCode(permission.getPermCode(), id)) {
            throw new BusinessException("权限编码已存在");
        }
        if (id.equals(permission.getParentId())) {
            throw new BusinessException("父权限不能是自身");
        }
        checkParent(permission.getParentId());
        permission.setId(id);
        applyDefaults(permission);
        permissionMapper.updateById(permission);
        return Result.success(permission);
    }

    @Operation(summary = "删除权限项(存在子节点时拒绝)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysPermission existing = permissionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("权限项不存在");
        }
        Long childCount = permissionMapper.selectCount(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("存在子权限项，禁止删除");
        }
        // sys_role_permission 外键 ON DELETE CASCADE，授权关系随之清除
        permissionMapper.deleteById(id);
        return Result.success();
    }

    private void validate(SysPermission permission) {
        if (!StringUtils.hasText(permission.getPermCode())) {
            throw new BusinessException("权限编码不能为空");
        }
        if (!StringUtils.hasText(permission.getPermName())) {
            throw new BusinessException("权限名称不能为空");
        }
        if (permission.getPermType() == null) {
            throw new BusinessException("权限类型不能为空");
        }
        if (permission.getParentId() == null) {
            permission.setParentId(0L);
        }
    }

    private void checkParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (permissionMapper.selectById(parentId) == null) {
            throw new BusinessException("父权限项不存在");
        }
    }

    private boolean existsCode(String permCode, Long excludeId) {
        LambdaQueryWrapper<SysPermission> w = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode);
        if (excludeId != null) {
            w.ne(SysPermission::getId, excludeId);
        }
        Long count = permissionMapper.selectCount(w);
        return count != null && count > 0;
    }

    private void applyDefaults(SysPermission permission) {
        permission.setChildren(null);
        if (permission.getSortOrder() == null) {
            permission.setSortOrder(0);
        }
        if (permission.getVisible() == null) {
            permission.setVisible(1);
        }
        if (permission.getStatus() == null) {
            permission.setStatus(1);
        }
    }
}
