package com.hjgd.plm.m04.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本链（59_m04_core_gap.sql 建表）。
 *
 * <p>该表无 deleted / updated_at 列，故不继承 BaseEntity；
 * 版本记录只增不改，唯一变动是把旧行的 is_current 置 0。
 */
@Data
@TableName("plm_document_version")
public class PlmDocumentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /** 主版本 V1.0 / V2.0 */
    private String version;

    /** 小版本（老 PLM 的 cp一位数 / V0-V9 序列），可空 */
    private String revision;

    private Long fileId;

    private String fileName;

    private Long fileSize;

    /** SHA-256（迁移对账口径），取不到时回落 plm_file.md5_hash */
    private String checksum;

    private String changeNote;

    /** WORKING / RELEASED */
    private String state;

    private String approvedBy;

    private LocalDateTime approvedAt;

    /** 1 = 当前生效版本 */
    private Integer isCurrent;

    private String createdBy;

    private LocalDateTime createdAt;
}
