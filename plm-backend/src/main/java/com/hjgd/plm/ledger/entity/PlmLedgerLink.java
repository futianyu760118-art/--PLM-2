package com.hjgd.plm.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 证据与业务对象的多态关联（AC-A4）。
 *
 * <p>模块/表单侧的「证据区」按本表联查 {@link PlmLedger} 渲染引用条目，
 * 不落第二份数据，因此两侧天然一致（AC-A3-3）。
 */
@Data
@TableName("plm_ledger_link")
public class PlmLedgerLink extends BaseEntity {

    private Long ledgerId;
    /** PROJECT / REQUIREMENT / MATERIAL / BOM / ECN / TASK / REVIEW */
    private String objectType;
    /** 目标业务键：project_no / issue_no / part_no / bom_no / ecn_no / 主键 */
    private String objectId;
    /** 冗余编号与名称：对象失效后仍可展示（AC-A4-5） */
    private String objectNo;
    private String objectName;
    /** ACTIVE / INVALID */
    private String linkStatus;
    /** DELETED / OBSOLETE */
    private String invalidReason;
}
