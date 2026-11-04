package com.hjgd.plm.m04.ecr.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.m04.ecr.enums.EcrChangeType;
import com.hjgd.plm.m04.ecr.enums.EcrStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ECR 工程变更申请。老 PLM 是 ECR→ECN 双单模型：ECR 讲清楚「为什么要改」，
 * 审批通过后转 ECN 才进入执行（升版、作废旧图、通知）。
 * status 在库里是 varchar + CHECK，所以这里用 @EnumValue 存 name()。
 */
@Data
@TableName("plm_ecr")
public class Ecr {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ecrNo;
    private String title;
    private String partNo;
    private String projectNo;
    @EnumValue
    private EcrChangeType changeType;
    private String changeReason;
    private String changeContent;
    private String impactAssessment;
    /** URGENT / NORMAL / PLAN */
    private String urgency;
    @EnumValue
    private EcrStatus status;
    private String applicant;
    private LocalDateTime applyTime;
    private String approvedBy;
    private LocalDateTime approvedAt;
    /** 转 ECN 后回填 */
    private String ecnNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted = 0;
}
