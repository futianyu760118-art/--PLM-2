package com.hjgd.plm.ledger.dto;

import lombok.Data;

/**
 * 台账 / 资料汇总处 的组合筛选条件（AC-A5-1、AC-B6-1）。
 *
 * <p>{@code scopeDeptId}/{@code scopeUserId} 由服务层按当前用户写入，<b>不接受外部入参</b>，
 * 为空表示不做行级收窄（台账管理员，AC-A6-1 角色表）。
 */
@Data
public class LedgerQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 20;

    /** 证据类型（AC-A5-1） */
    private String type;
    /** 责任人（AC-A5-1） */
    private Long ownerId;
    /** 来源部门（AC-A5-1） */
    private Long deptId;
    /** 状态（AC-A5-1） */
    private String status;
    /** 关键字：命中 证据名称 / 证据编号 / 备注 任一（AC-A5-4） */
    private String keyword;
    /** 发生时间范围，yyyy-MM-dd，含当日（AC-A5-3） */
    private String occurStart;
    private String occurEnd;
    /** 上传时间范围，yyyy-MM-dd，含当日（AC-A5-1） */
    private String uploadStart;
    private String uploadEnd;
    /** 关联对象类型（AC-A5-1） */
    private String objectType;
    /** 关联对象编号/名称关键字（AC-A5-1） */
    private String objectKeyword;
    /** 来源证据编号模糊匹配（AC-B6-1） */
    private String ledgerNoLike;

    private String sortBy;
    private String sortOrder;

    /** 行级范围：本部门 */
    private Long scopeDeptId;
    /** 行级范围：本人创建 */
    private Long scopeUserId;
}
