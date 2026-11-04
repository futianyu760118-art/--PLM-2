package com.hjgd.plm.m04.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务：一行 = 一个审批人的一次待办。
 *
 * <p>{@code action} 为 null 表示仍在等待该审批人处理（即待办箱数据来源）；
 * 处理后被填成 APPROVE / REJECT / BACK / TERMINATE / CANCELLED（流程流转导致作废）。
 * 因此本表既是待办队列也是完整动作流水，不需要额外表。</p>
 */
@Data
@TableName("plm_approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Integer nodeSeq;
    private String approver;
    private String action;
    private String comment;
    private LocalDateTime actedAt;
    private LocalDateTime createdAt;
}
