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
}
