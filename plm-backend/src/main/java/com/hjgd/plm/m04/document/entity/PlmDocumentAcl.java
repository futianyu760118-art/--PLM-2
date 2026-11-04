package com.hjgd.plm.m04.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档对象级权限（59_m04_core_gap.sql 建表）。
 *
 * <p>没有 ACL 记录时按密级走默认策略（见 DocumentServiceImpl#assertReadable），
 * 一旦有记录就以记录为准。
 */
@Data
@TableName("plm_document_acl")
public class PlmDocumentAcl {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /** USER / ROLE / DEPT / PROJECT_ROLE */
    private String subjectType;

    private String subjectId;

    private Integer canRead;

    private Integer canWrite;

    private Integer canCheckout;

    private Integer canDelete;

    private String grantedBy;

    private LocalDateTime createdAt;
}
