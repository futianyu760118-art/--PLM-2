package com.hjgd.plm.common.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务列表查询的数据范围隔离（R4 / F13）。
 *
 * <p>标注在 service 查询方法上；当前登录用户的角色 {@code data_scope} 决定过滤强度：
 * 1=全部（不过滤）、2=本部门、3=本人。系统管理类接口不标注，不套用。
 *
 * <p>{@code tables} 必须显式声明参与过滤的表名：多表联查时只有命中的表会被追加条件，
 * 避免误伤无关表（不声明则不生效）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 参与数据范围过滤的表名（可含 schema 前缀，比较时忽略大小写与引号） */
    String[] tables();

    /** 部门隔离列名（data_scope=2 时使用） */
    String deptColumn() default "dept_id";

    /** 本人隔离列名（data_scope=3 时使用） */
    String userColumn() default "owner_id";

    /** userColumn 的取值口径：true=当前用户 ID，false=当前登录名 */
    boolean userColumnIsId() default true;
}
