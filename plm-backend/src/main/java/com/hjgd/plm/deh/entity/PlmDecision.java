package com.hjgd.plm.deh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DEH 决策（{@code plm_decision}，DEH_CONTRACT_V1 §3）。
 *
 * <p>Decision 只保存对上游对象的引用（{@code source_type/source_id}），
 * 不拥有上游主数据 —— 跨域只读引用，避免形成第二事实源。
 */
@Data
@TableName("plm_decision")
public class PlmDecision {

    @TableId(type = IdType.INPUT)
    private String decisionId;
    private String decisionCaseId;
    private String goalId;
    /** meeting | event | issue | agent | human | system */
    private String sourceType;
    private String sourceId;
    private String objectType;
    private String objectId;
    private String content;
    private String reason;
    private String decisionOwnerId;
    /** draft | confirmed | superseded | cancelled */
    private String status;
    private LocalDateTime decidedAt;
    private String evidenceRefs;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
