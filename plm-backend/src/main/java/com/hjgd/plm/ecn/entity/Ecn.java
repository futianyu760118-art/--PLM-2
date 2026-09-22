package com.hjgd.plm.ecn.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import com.hjgd.plm.ecn.enums.EcnChangeType;
import com.hjgd.plm.ecn.enums.EcnStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("plm_ecn")
public class Ecn extends BaseEntity {

    private String ecnNo;
    private String partNo;
    private Long materialId;
    private String materialName;
    @EnumValue
    private EcnChangeType changeType;
    private String versionBefore;
    private String versionAfter;
    private String changeLocation;
    private String changeReason;
    private BigDecimal moldCost;
    private String impactScope;
    private String trialImpact;
    private String massImpact;
    @EnumValue
    private EcnStatus status;
    private String applicant;
    private LocalDateTime applyTime;
    private String reviewL1By;
    private LocalDateTime reviewL1Time;
    private String reviewL1Comment;
    private String reviewL2By;
    private LocalDateTime reviewL2Time;
    private String reviewL2Comment;
    private String finalComment;
    private LocalDateTime effectiveTime;
    private LocalDateTime voidTime;
    private String createdBy;
}
