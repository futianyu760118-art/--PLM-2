package com.hjgd.plm.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AEOS 统一证据对象（F09）。
 *
 * <p>契约依据 {@code AEOS/contracts/DEH_CONTRACT_V1.md §6 Evidence Contract}：
 * 四要素 source / version / checksum / confidence 齐备，subject 指向被证明的对象，
 * {@code sourceRecordId} 指向原始记录，形成
 * 「管理结果 → 决策 → 动作 → 结果 → 原始记录」的可穿透链路。
 *
 * <p>档案引用规则：一份文件多处引用、不复制文件 —— 本表只保存引用
 * （{@code sourceRecordId} = {@code plm_file.id}），不复制文件本体。
 */
@Data
@TableName("plm_evidence")
public class PlmEvidence {

    @TableId(type = IdType.INPUT)
    private String evidenceId;
    private String subjectType;
    private String subjectId;
    private String evidenceType;
    private String sourceSystem;
    private String sourceRecordId;
    private String uri;
    private String checksum;
    private String version;
    private BigDecimal confidence;
    private String provenance;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String metadata;
    private String projectNo;
    private String legacyRefType;
    private String legacyRefId;
}
