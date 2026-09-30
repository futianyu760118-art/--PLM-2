package com.hjgd.plm.feedback.dto;

import lombok.Data;

/**
 * 反馈提交入参。字段级规则(trim 后长度、REQUIREMENT 必填期望结果)
 * 统一在 FeedbackService 内一次性收集后抛出,以满足 AC F8「一次列出全部错误」。
 */
@Data
public class FeedbackSubmitDTO {

    /** 缺陷/建议/需求 三选一,无默认值 */
    private String type;

    /** 反馈内容,trim 后 10-2000 字符 */
    private String content;

    /** 期望结果,0-2000 字符;type=REQUIREMENT 时必填 */
    private String expectedResult;

    /** 来源页面路由路径,前端只读带入,服务端仍重新校验 */
    private String pagePath;

    /** 可选联系方式,0-100 字符,不做格式校验 */
    private String contact;
}
