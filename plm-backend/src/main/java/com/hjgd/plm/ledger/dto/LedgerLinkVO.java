package com.hjgd.plm.ledger.dto;

import lombok.Data;

/**
 * 关联对象展示项（AC-A4-2 列表 + 跳转；AC-A4-5 失效率与原因）。
 *
 * <p>{@code objectNo}/{@code objectName} 取引用表冗余值，即使目标对象已删除/作废仍可展示；
 * {@code objectValid}/{@code invalidReason} 来自实时解析，用于给出行内失效提示。
 */
@Data
public class LedgerLinkVO {

    private Long id;
    private String objectType;
    private String objectId;
    private String objectNo;
    private String objectName;
    /** ACTIVE / INVALID */
    private String linkStatus;
    /** DELETED 已删除 / OBSOLETE 已作废 */
    private String invalidReason;
    /** 目标对象当前是否仍存在且有效 */
    private Boolean objectValid;
    /** 前端跳转路径（对象已删除时仍给出模块列表页） */
    private String jumpPath;
}
