package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysDepartment;
import com.hjgd.plm.system.mapper.SysDepartmentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门/组织维护（F14 / AC14.1、AC14.3）。
 *
 * <p>此前 sys_department 只能由 SQL 种子写入，无维护接口，EBMS 组织也无法对齐——
 * 本控制器补齐部门树维护能力。删除被引用的部门会被拒绝，避免 user.dept_id 悬空。
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final SysDepartmentMapper departmentMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    public Result<List<SysDepartment>> tree() {
        List<SysDepartment> all = departmentMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>()
                        .orderByAsc(SysDepartment::getSortOrder)
                        .orderByAsc(SysDepartment::getId));
        Map<Long, SysDepartment> byId = new LinkedHashMap<>();
        for (SysDepartment d : all) {
            SysDepartment node = new SysDepartment();
            node.setId(d.getId());
            node.setDeptCode(d.getDeptCode());
            node.setDeptName(d.getDeptName());
            node.setParentId(d.getParentId());
            node.setSortOrder(d.getSortOrder());
            node.setStatus(d.getStatus());
            node.setCreatedAt(d.getCreatedAt());
            node.setUpdatedAt(d.getUpdatedAt());
            node.setChildren(new ArrayList<>());
            byId.put(node.getId(), node);
        }
        List<SysDepartment> roots = new ArrayList<>();
        for (SysDepartment node : byId.values()) {
            Long parentId = node.getParentId();
            SysDepartment parent = (parentId == null || parentId == 0L) ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return Result.success(roots);
    }

    @Operation(summary = "新增部门")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysDepartment> create(@RequestBody SysDepartment department) {
        validate(department);
        if (existsCode(department.getDeptCode(), null)) {
            throw new BusinessException("部门编码已存在");
        }
        checkParent(department.getParentId(), null);
        department.setId(null);
        department.setChildren(null);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        applyDefaults(department);
        departmentMapper.insert(department);
        return Result.success(department);
    }

    @Operation(summary = "修改部门")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<SysDepartment> update(@PathVariable Long id, @RequestBody SysDepartment department) {
        SysDepartment existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("部门不存在");
        }
        validate(department);
        if (existsCode(department.getDeptCode(), id)) {
            throw new BusinessException("部门编码已存在");
        }
        checkParent(department.getParentId(), id);
        department.setId(id);
        department.setChildren(null);
        department.setUpdatedAt(LocalDateTime.now());
        applyDefaults(department);
        departmentMapper.updateById(department);
        return Result.success(department);
    }

    @Operation(summary = "删除部门(存在子部门或被用户引用时拒绝)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysDepartment existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("部门不存在");
        }
        Long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<SysDepartment>().eq(SysDepartment::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("存在下级部门，禁止删除");
        }
        // 含逻辑删除用户：外键仍指向本部门，物理删除会直接违反约束
        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE dept_id = ?", Long.class, id);
        if (userCount != null && userCount > 0) {
            throw new BusinessException("部门已被用户引用，禁止删除");
        }
        departmentMapper.deleteById(id);
        return Result.success();
    }

    private void validate(SysDepartment department) {
        if (!StringUtils.hasText(department.getDeptCode())) {
            throw new BusinessException("部门编码不能为空");
        }
        if (!StringUtils.hasText(department.getDeptName())) {
            throw new BusinessException("部门名称不能为空");
        }
        if (department.getParentId() == null) {
            department.setParentId(0L);
        }
    }

    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw new BusinessException("上级部门不能是自身");
        }
        if (departmentMapper.selectById(parentId) == null) {
            throw new BusinessException("上级部门不存在");
        }
    }

    private boolean existsCode(String deptCode, Long excludeId) {
        LambdaQueryWrapper<SysDepartment> w = new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getDeptCode, deptCode);
        if (excludeId != null) {
            w.ne(SysDepartment::getId, excludeId);
        }
        Long count = departmentMapper.selectCount(w);
        return count != null && count > 0;
    }

    private void applyDefaults(SysDepartment department) {
        if (department.getSortOrder() == null) {
            department.setSortOrder(0);
        }
        if (department.getStatus() == null) {
            department.setStatus(1);
        }
    }
}
