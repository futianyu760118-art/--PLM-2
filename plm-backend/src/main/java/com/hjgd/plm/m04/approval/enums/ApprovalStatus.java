package com.hjgd.plm.m04.approval.enums;

/**
 * 审批实例状态。
 *
 * <p>RUNNING 期间由 {@code current_seq} 表示进度；终态只有三种：
 * APPROVED（全部节点通过）、REJECTED（被驳回/终止）、CANCELLED（申请人撤回）。</p>
 */
public enum ApprovalStatus {
    RUNNING,    // 审批中
    APPROVED,   // 已通过
    REJECTED,   // 已驳回
    CANCELLED   // 已撤回
}
