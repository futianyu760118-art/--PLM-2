package com.hjgd.plm.log.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解，标记需要留痕的接口方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型描述 (如: 新增物料 / ECN审批 / 文件下载)
     */
    String value();

    /**
     * 关联料号SpEL表达式 (如 #partNo)
     */
    String partNo() default "";

    /**
     * 文件版本SpEL表达式
     */
    String fileVersion() default "";

    /**
     * 入参摘要SpEL表达式。默认留空时取 args[0] 的 JSON, 这只适合「首个入参就是业务主体」的接口;
     * 形如 {@code @PostMapping("/{id}/xxx")}(Long id, XxxDTO dto) 的接口, args[0] 只是路径变量,
     * 留空会把日志记成无意义的 "1"。此类接口须显式给出表达式, 例如
     * {@code params = "'targetStatus=' + (#dto.targetStatus ?: '')"}。
     */
    String params() default "";
}
