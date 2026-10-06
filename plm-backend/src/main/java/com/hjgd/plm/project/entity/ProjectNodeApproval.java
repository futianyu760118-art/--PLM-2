package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_project_node_approval")
public class ProjectNodeApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long projectId;
    private String nodeCode;
    private String approvalLevel;
    private Integer approvalSeq;
    private Integer approvalRound;
    private String status;
    private Long submittedById;
    private String submittedByName;
    private Long approverId;
    private String approverName;
    private String comment;
    private String evidenceSnapshot;
    private String requestId;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
