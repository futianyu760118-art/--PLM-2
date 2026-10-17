package com.hjgd.plm.common;

import com.hjgd.plm.auth.security.SecurityUtils;

/**
 * 数据可见性域：默认所有人都能看到全部内容；用户可自选「看全部 / 仅本人 / 仅他人」。
 * 语义: 1=全部(默认), 2=仅他人(排除自己), 3=仅本人。ADMIN 始终全部。
 * 后端兜底：所有「显示内容」列表接口统一按 scope 施加过滤，前端切换仅是体验层。
 */
public final class DataScope {

    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_OTHERS = 2;
    public static final int SCOPE_SELF = 3;

    private DataScope() {}

    public static boolean admin() {
        try { return SecurityUtils.hasRole("ADMIN"); } catch (Exception e) { return true; }
    }

    /** 当前数据范围: 1=全部, 2=仅他人, 3=仅本人；ADMIN/null 视为全部 */
    public static int scope() {
        if (admin()) return SCOPE_ALL;
        try {
            int s = SecurityUtils.getCurrentUser().getDataScope();
            return (s == SCOPE_OTHERS || s == SCOPE_SELF) ? s : SCOPE_ALL;
        } catch (Exception e) {
            return SCOPE_ALL;
        }
    }

    public static boolean seesAll() {
        return scope() == SCOPE_ALL;
    }

    public static String name() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; }
    }

    public static Long uid() {
        try { return SecurityUtils.getCurrentUserId(); } catch (Exception e) { return 0L; }
    }

    /** SQL 片段：项目表某 owner 列的三态过滤 */
    public static String projectOwner(String ownerCol) {
        switch (scope()) {
            case SCOPE_SELF: return ownerCol + "='" + esc(name()) + "'";
            case SCOPE_OTHERS: return "(" + ownerCol + " IS DISTINCT FROM '" + esc(name()) + "')";
            default: return "1=1";
        }
    }

    /** SQL 片段：按「本人负责的项目编号集合」的三态过滤（工作表/证据/变更等带 project_no 的表） */
    public static String myProjects(String projectNoCol) {
        switch (scope()) {
            case SCOPE_SELF: return projectNoCol + " IN (SELECT project_no FROM plm_project WHERE owner='" + esc(name()) + "')";
            case SCOPE_OTHERS: return projectNoCol + " NOT IN (SELECT project_no FROM plm_project WHERE owner='" + esc(name()) + "')";
            default: return "1=1";
        }
    }

    /** SQL 片段：立项 applicant/owner 两列的三态过滤 */
    public static String initiationScope(String applicantCol, String ownerCol) {
        String me = esc(name());
        switch (scope()) {
            case SCOPE_SELF: return "(" + applicantCol + "='" + me + "' OR " + ownerCol + "='" + me + "')";
            case SCOPE_OTHERS: return "(" + applicantCol + " IS DISTINCT FROM '" + me + "' AND " + ownerCol + " IS DISTINCT FROM '" + me + "')";
            default: return "1=1";
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
