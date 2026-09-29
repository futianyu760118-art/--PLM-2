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
}
