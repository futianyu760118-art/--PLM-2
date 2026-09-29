package com.hjgd.plm.common.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

/**
 * 把 {@link DataScopeContext} 中的口径翻译成 SQL 条件（R4 / F13）。
 *
 * <p>仅当上下文存在、且当前表在被标注的 {@code tables} 内时才追加条件；
 * 其余查询（系统管理、内部任务、非标注方法）原样放行。
 *
 * <p>条件用 JSqlParser 的表达式对象拼装，不做字符串拼接，避免注入。
 */
@Component
public class PlmDataPermissionHandler implements MultiDataPermissionHandler {

    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_DEPT = 2;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataScopeContext.Scope ctx = DataScopeContext.get();
        if (ctx == null || table == null) {
            return null;
        }
        String tableName = DataScopeAspect.normalize(table.getName());
        if (!ctx.tables().contains(tableName)) {
            return null;
        }
        int scope = ctx.dataScope();
        if (scope == SCOPE_ALL) {
            return null;
        }
        String qualifier = (table.getAlias() != null) ? table.getAlias().getName() : table.getName();
        if (scope == SCOPE_DEPT) {
            // 部门口径但账号无部门：不能退化为「全部」，用恒假条件封闭结果集
            return ctx.deptId() == null
                    ? alwaysFalse()
                    : equalsTo(qualifier, ctx.deptColumn(), new LongValue(ctx.deptId()));
        }
        if (ctx.userColumnIsId()) {
            return ctx.userId() == null
                    ? alwaysFalse()
                    : equalsTo(qualifier, ctx.userColumn(), new LongValue(ctx.userId()));
        }
        return ctx.username() == null
                ? alwaysFalse()
                : equalsTo(qualifier, ctx.userColumn(), stringLiteral(ctx.username()));
    }

    /**
     * JSqlParser 的 {@link StringValue} 按「已转义」语义接收文本（其 toString 直接外包单引号，
     * 不再转义），故此处显式把单引号翻倍，避免登录名携带引号时形成注入面。
     */
    private Expression stringLiteral(String raw) {
        StringValue value = new StringValue();
        value.setValue(raw.replace("'", "''"));
        return value;
    }

    private Expression equalsTo(String qualifier, String column, Expression value) {
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(new Table(qualifier), column));
        eq.setRightExpression(value);
        return eq;
    }

    /** 1 = 0 */
    private Expression alwaysFalse() {
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new LongValue(1));
        eq.setRightExpression(new LongValue(0));
        return eq;
    }
}
