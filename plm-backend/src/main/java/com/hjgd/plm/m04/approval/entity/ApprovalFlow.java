package com.hjgd.plm.m04.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流定义。flow_code 唯一，object_type 决定它服务哪类单据。
 */
@Data
@TableName("plm_approval_flow")
public class ApprovalFlow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowCode;
    private String objectType;
    private String name;
    private String versionNo;
    /** DRAFT / ENABLED / DISABLED，只有 ENABLED 会被引擎选中 */
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
