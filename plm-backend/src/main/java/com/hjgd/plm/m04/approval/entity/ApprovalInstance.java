package com.hjgd.plm.m04.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例：一张单据一条，唯一键 (object_type, object_no, flow_id)。
 * 驳回后重新提交复用同一条实例，只把状态与 currentSeq 复位。
 */
@Data
@TableName("plm_approval_instance")
public class ApprovalInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private String objectType;
    private String objectNo;
    private Integer currentSeq;
    /** RUNNING / APPROVED / REJECTED / CANCELLED */
    private String status;
    private String startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 发起时的业务上下文 JSON，供后续节点条件判断与审计 */
    private String contextJson;
}
