package com.hjgd.plm.ecn.enums;

public enum EcnStatus {
    DRAFT,       // 草稿
    PENDING_L1,  // 待一审(研发主管)
    PENDING_L2,  // 待二审(供应链总监)
    APPROVED,    // 审批通过
    EFFECTING,   // 生效中(生效管线执行期间, v5 §4.2)
    REJECTED,    // 审批驳回
    FAILED,      // 生效失败(管线异常, 可重试, v5 §4.2)
    EFFECTIVE,   // 已生效
    VOID         // 已作废
}
