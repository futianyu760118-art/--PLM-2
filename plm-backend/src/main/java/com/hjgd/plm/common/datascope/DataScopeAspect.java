package com.hjgd.plm.common.datascope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.system.entity.SysRole;
import com.hjgd.plm.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析当前用户的生效数据范围并写入 {@link DataScopeContext}，供 SQL 拦截器使用（R4）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    /** 全部=1（最宽松）、本部门=2、本人=3（最严格） */
    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_DEPT = 2;
    private static final int SCOPE_SELF = 3;

    private final SysRoleMapper roleMapper;

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        DataScopeContext.Scope previous = DataScopeContext.get();
        try {
            DataScopeContext.Scope scope = resolve(dataScope);
            if (scope != null) {
                DataScopeContext.set(scope);
            }
            return joinPoint.proceed();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }

    private DataScopeContext.Scope resolve(DataScope ann) {
        if (ann.tables().length == 0) {
            return null;
        }
        LoginUser user;
        try {
            user = SecurityUtils.getCurrentUser();
        } catch (Exception e) {
            // 无登录上下文（如内部任务）不施加过滤
            return null;
        }
        Set<String> tables = new HashSet<>();
        for (String table : ann.tables()) {
            tables.add(normalize(table));
        }
        return new DataScopeContext.Scope(
                resolveDataScope(user),
                user.getUser().getDeptId(),
                user.getUserId(),
                user.getUsername(),
                tables,
                ann.deptColumn(),
                ann.userColumn(),
                ann.userColumnIsId());
    }

    /**
     * 生效范围取用户全部角色中最宽松者（数值最小）——与「权限取角色并集」的 RBAC 语义一致。
     * 无角色账号按最严格（本人）处理。
     */
    private int resolveDataScope(LoginUser user) {
        List<String> roleCodes = user.getRoles();
        if (roleCodes == null || roleCodes.isEmpty()) {
            return SCOPE_SELF;
        }
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, roleCodes));
        if (roles == null || roles.isEmpty()) {
            return SCOPE_SELF;
        }
        int effective = SCOPE_SELF;
        for (SysRole role : roles) {
            Integer scope = role.getDataScope();
            int value = (scope == null) ? SCOPE_ALL : scope;
            if (value == SCOPE_ALL) {
                return SCOPE_ALL;
            }
            effective = Math.min(effective, value);
        }
        return effective;
    }

    /** 表名归一：去引号/schema 前缀，统一小写 */
    static String normalize(String tableName) {
        if (tableName == null) {
            return "";
        }
        String t = tableName.trim().replace("`", "").replace("\"", "");
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t.toLowerCase(Locale.ROOT);
    }
}
