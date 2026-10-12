package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关键节点双级审批记录（研发主管 L1 → 总经理 L2）。
 * 保留全部历史，不设唯一约束：当前有效审批 = 每级最新一条，
 * REVOKED 表示撤回（支持驳回 / 回退 / 重开后重新审批）。
 */
@Data
@TableName("plm_project_node_approval")
public class ProjectNodeApproval {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REVOKED = "REVOKED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long projectId;
    private String projectNo;
    private String nodeCode;
    private Integer level;
    private String roleCode;
    private String approver;
    private String decision;
    private String comment;
    private LocalDateTime decidedAt;
}
