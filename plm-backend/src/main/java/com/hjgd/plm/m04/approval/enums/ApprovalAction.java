package com.hjgd.plm.m04.approval.enums;

/**
 * 审批动作。与 {@code plm_approval_node.on_reject} 配合决定驳回后的走向。
 */
public enum ApprovalAction {
    /** 通过当前节点 */
    APPROVE,
    /** 驳回，按节点 on_reject 策略执行（BACK 退回上节点 / TERMINATE 终止 / TO_APPLICANT 退回申请人） */
    REJECT,
    /** 强制退回上一节点，忽略 on_reject 策略 */
    BACK,
    /** 强制终止审批 */
    TERMINATE
}
