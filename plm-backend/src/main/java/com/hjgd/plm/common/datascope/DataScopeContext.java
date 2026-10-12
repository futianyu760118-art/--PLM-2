package com.hjgd.plm.common.datascope;

import java.util.Set;

/**
 * {@link DataScope} 的线程内上下文：AOP 切面在进入被标注方法时写入，
 * MyBatis 拦截器在改写 SQL 时读取。未写入即表示当前查询不受数据范围约束。
 */
public final class DataScopeContext {

    /** 一次查询的过滤口径 */
    public record Scope(int dataScope,
                        Long deptId,
                        Long userId,
                        String username,
                        Set<String> tables,
                        String deptColumn,
                        String userColumn,
                        boolean userColumnIsId) {
    }

    private static final ThreadLocal<Scope> HOLDER = new ThreadLocal<>();

    public static void set(Scope scope) {
        HOLDER.set(scope);
    }

    public static Scope get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private DataScopeContext() {
    }
}
