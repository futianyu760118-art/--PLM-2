package com.hjgd.plm.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aeos_evidence")
public class AeosEvidence {
    @TableId(type = IdType.INPUT)
    private String evidenceId;
    private String domain;
    private Long projectId;
    private String productId;
    private String productVersion;
    private String objectType;
    private String objectId;
    private String objectVersion;
    private String evidenceType;
    private String sourceType;
    private String sourceSystem;
    private Long fileId;
    private String fileUri;
    private String contentRef;
    private String recordRef;
    private String hash;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String relatedGate;
    private String relatedDecision;
    private String relatedAction;
    private String relatedResult;
    private String versionNo;
}
