package com.hjgd.plm.m04.ecr.enums;

/**
 * ECR 状态。与 plm_ecr 的 CHECK 约束保持一致。
 * CONVERTED = 已转成 ECN，是流程终点。
 */
public enum EcrStatus {
    DRAFT,       // 草稿
    REVIEWING,   // 审批中
    APPROVED,    // 审批通过（可转 ECN）
    REJECTED,    // 驳回
    CONVERTED,   // 已转 ECN
    CLOSED       // 关闭
}
