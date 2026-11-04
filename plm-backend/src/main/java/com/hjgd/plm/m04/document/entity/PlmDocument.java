package com.hjgd.plm.m04.document.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import com.hjgd.plm.m04.document.enums.DocLifecycleState;
import com.hjgd.plm.m04.document.enums.DocType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档主表（59_m04_core_gap.sql 建表）。
 *
 * <p>对应老 PLM 的"文档 + 五区"，是新系统里原本完全缺失的实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plm_document")
public class PlmDocument extends BaseEntity {

    /** 文档编号，走编码引擎 DOC 规则 */
    private String docNo;

    private String title;

    @EnumValue
    private DocType docType;

    /** 可空：体系文件不挂料号 */
    private String partNo;

    private String projectNo;

    /** 关联 plm_archive_tree 节点（料号档案目录），T07 用 */
    private Long archiveNodeId;

    @EnumValue
    private DocLifecycleState lifecycleState;

    /** 当前生效版本号，例 V2.0 */
    private String currentVersion;

    private String owner;

    /** PUBLIC / INTERNAL / SECRET */
    private String secretLevel;

    /** 检入检出锁：当前锁定人，null = 未锁定 */
    private String checkedOutBy;

    private LocalDateTime checkedOutAt;

    private String createdBy;

    private String remark;
}
