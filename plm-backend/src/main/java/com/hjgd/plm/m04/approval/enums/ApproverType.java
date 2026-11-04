package com.hjgd.plm.m04.approval.enums;

/**
 * 节点审批人类型。
 *
 * <p>实现说明：{@code sys_department} 没有主管列，{@code DEPT_LEADER} 暂时降级为
 * 「把 approver_value 当角色码解析」，待部门主管字段补齐后改为真实解析。
 * {@code POSITION} / {@code PROJECT_ROLE} 同样先按角色码降级处理，
 * 保证流程配置不会因为解析不到人而卡死（解析为空最终回落 ADMIN）。</p>
 */
public enum ApproverType {
    ROLE,           // 角色（approver_value = role_code，可用逗号分隔多个）
    USER,           // 指定人（approver_value = username，可逗号分隔）
    POSITION,       // 岗位（降级为角色解析）
    PROJECT_ROLE,   // 项目角色（降级为角色解析）
    DEPT_LEADER     // 部门主管（暂降级为角色解析，见类注释）
}
