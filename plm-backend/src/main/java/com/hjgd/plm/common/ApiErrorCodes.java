package com.hjgd.plm.common;

public final class ApiErrorCodes {
    private ApiErrorCodes() {}

    public static final String DQ_BLOCKED = "DQ_BLOCKED";
    public static final String LIFECYCLE_DENIED = "LIFECYCLE_DENIED";
    public static final String CODE_CONFLICT = "CODE_CONFLICT";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    /** 越权审批（F07 双级审批角色约束） */
    public static final String APPROVAL_DENIED = "APPROVAL_DENIED";
    /** Gate 未通过 / 判定未知（F08） */
    public static final String GATE_DENIED = "GATE_DENIED";
    /** 证据不满足四要素 / 不可穿透（F09） */
    public static final String EVIDENCE_INVALID = "EVIDENCE_INVALID";
    /** 决策链路非法（源引用缺失 / 状态不允许该操作）（F16） */
    public static final String DECISION_INVALID = "DECISION_INVALID";
    /** 结果验证被拒：非独立验证 / 无验证证据（done ≠ verified）（F16） */
    public static final String RESULT_VERIFY_DENIED = "RESULT_VERIFY_DENIED";
    /** 学习条目不满足发布门槛（F16） */
    public static final String LEARNING_NOT_PUBLISHABLE = "LEARNING_NOT_PUBLISHABLE";
}
