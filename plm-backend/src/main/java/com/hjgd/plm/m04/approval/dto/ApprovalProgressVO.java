package com.hjgd.plm.m04.approval.dto;

import com.hjgd.plm.m04.approval.entity.ApprovalNode;
import com.hjgd.plm.m04.approval.entity.ApprovalTask;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批进度（页面画时间轴用）：节点定义 + 已发生的动作。
 */
@Data
public class ApprovalProgressVO {

    private Long instanceId;
    private String objectType;
    private String objectNo;
    private String status;
    private Integer currentSeq;
    private String startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ApprovalNode> nodes;
    private List<ApprovalTask> tasks;
}
