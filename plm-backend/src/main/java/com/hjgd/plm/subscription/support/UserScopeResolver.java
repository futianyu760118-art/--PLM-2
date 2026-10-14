package com.hjgd.plm.subscription.support;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 解析某个用户的行级可见范围，供报告生成等「替他人取数」的场景使用（AC-B6-9）。
 *
 * <p>报告由后台任务生成，那一刻没有 SecurityContext：{@code SecurityUtils} 会直接抛未授权，
 * 台账侧的 {@code requireReadable} 也用不了。所以范围必须在没有登录态的前提下、
 * 按订阅人的身份算出来，口径与前台请求保持一致。
 */
@Component
@RequiredArgsConstructor
public class UserScopeResolver {

    /**
     * @param deptId       本部门；unrestricted 为 true 时不参与收窄
     * @param userId       本人（创建人/责任人）
     * @param unrestricted 是否为台账管理员（不过滤，AC-A6-1 角色表）
     */
    public record Scope(Long deptId, Long userId, boolean unrestricted) {
    }

    private final JdbcTemplate jdbcTemplate;

    /** 当前登录用户的范围（口径与台账侧 {@code isAdmin()} 一致，直接用登录态里的角色/权限） */
    public Scope current() {
        LoginUser user = SecurityUtils.getCurrentUser();
        return new Scope(user.getUser().getDeptId(), user.getUserId(), isAdmin(user));
    }

    /** 指定用户的范围（后台任务用） */
    public Scope resolve(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT dept_id FROM sys_user WHERE id = ?", userId);
        Long deptId = rows.isEmpty() ? null : toLong(rows.get(0).get("dept_id"));
        boolean admin = isAdminByDb(userId);
        return new Scope(deptId, userId, admin);
    }

    private boolean isAdmin(LoginUser user) {
        if (user.getRoles() != null && (user.getRoles().contains("ADMIN") || user.getRoles().contains("LEDGER_ADMIN"))) {
            return true;
        }
        return user.getPermissions() != null && user.getPermissions().contains("ledger:admin");
    }

    private boolean isAdminByDb(Long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 1 "
                        + "AND (r.role_code IN ('ADMIN','LEDGER_ADMIN') "
                        + "     OR EXISTS (SELECT 1 FROM sys_role_permission rp "
                        + "                JOIN sys_permission p ON p.id = rp.permission_id "
                        + "                WHERE rp.role_id = r.id AND p.perm_code = 'ledger:admin'))",
                Integer.class, userId);
        return n != null && n > 0;
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(o).trim());
    }
}
