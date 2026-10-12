package com.hjgd.plm.common.datascope;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAND-117 / R4 / F13：数据范围 SQL 改写。
 * <p>核心不变量：口径无法满足时封闭结果集（1 = 0），绝不退化为「全部可见」。
 */
@DisplayName("PlmDataPermissionHandler: 数据范围改写")
class PlmDataPermissionHandlerTest {

    private final PlmDataPermissionHandler handler = new PlmDataPermissionHandler();

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private void scoped(int dataScope, Long deptId, Long userId, String username) {
        DataScopeContext.set(new DataScopeContext.Scope(
                dataScope, deptId, userId, username,
                Set.of("plm_issue"), "dept_id", "owner_id", true));
    }

    private Expression segment(String tableName) {
        return handler.getSqlSegment(new Table(tableName), null, "any.statement");
    }

    @Test
    @DisplayName("无上下文（未标注方法）时不改写任何 SQL")
    void shouldPassThroughWithoutContext() {
        assertNull(segment("plm_issue"));
    }

    @Test
    @DisplayName("表不在标注清单内时放行")
    void shouldPassThroughUnlistedTable() {
        scoped(3, null, 7L, "u");
        assertNull(segment("sys_user"));
    }

    @Test
    @DisplayName("口径=1 全部数据 放行")
    void shouldPassThroughScopeAll() {
        scoped(1, null, 7L, "u");
        assertNull(segment("plm_issue"));
    }

    @Test
    @DisplayName("口径=2 本部门 追加 dept_id = ?")
    void shouldFilterByDept() {
        scoped(2, 42L, null, null);
        Expression e = segment("plm_issue");
        assertNotNull(e);
        String sql = e.toString();
        assertTrue(sql.contains("dept_id"), sql);
        assertTrue(sql.contains("42"), sql);
    }

    @Test
    @DisplayName("口径=3 仅本人 追加 owner_id = ?")
    void shouldFilterByOwner() {
        scoped(3, null, 7L, "zhang");
        Expression e = segment("plm_issue");
        assertNotNull(e);
        String sql = e.toString();
        assertTrue(sql.contains("owner_id"), sql);
        assertTrue(sql.contains("7"), sql);
    }

    @Test
    @DisplayName("部门口径但账号无部门 → 1 = 0，不得放行全部")
    void deptScopeWithoutDeptMustNotLeak() {
        scoped(2, null, 7L, "zhang");
        Expression e = segment("plm_issue");
        assertNotNull(e, "无部门时必须封闭结果集而不是放行");
        assertTrue(e.toString().replaceAll("\\s+", "").contains("1=0"), e.toString());
    }

    @Test
    @DisplayName("本人口径但账号无 id → 1 = 0，不得放行全部")
    void selfScopeWithoutUserIdMustNotLeak() {
        scoped(3, null, null, null);
        Expression e = segment("plm_issue");
        assertNotNull(e);
        assertTrue(e.toString().replaceAll("\\s+", "").contains("1=0"), e.toString());
    }

    @Test
    @DisplayName("用户名按字段匹配时，单引号必须转义（防注入）")
    void usernameWithQuoteMustBeEscaped() {
        DataScopeContext.set(new DataScopeContext.Scope(
                3, null, null, "o'brien",
                Set.of("plm_issue"), "dept_id", "owner_name", false));
        Expression e = segment("plm_issue");
        assertNotNull(e);
        String sql = e.toString();
        // JSqlParser 的 StringValue 不再转义，实现须自行翻倍单引号
        assertTrue(sql.contains("o''brien"), "单引号应被翻倍，实际: " + sql);
        assertFalse(sql.contains("o'brien'"), "不得出现未转义的单引号: " + sql);
    }

    @Test
    @DisplayName("表名带 schema 前缀/引号时仍能匹配标注清单")
    void shouldNormalizeTableName() {
        scoped(2, 42L, null, null);
        assertNotNull(segment("\"public\".\"plm_issue\""));
    }
}
