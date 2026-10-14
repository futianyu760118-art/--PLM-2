package com.hjgd.plm.ledger.dto;

import lombok.Data;

/** 关联对象入参（AC-A4-1）：类型 + 业务键。 */
@Data
public class LinkRequest {

    /** PROJECT / REQUIREMENT / MATERIAL / BOM / ECN / TASK / REVIEW */
    private String objectType;
    /** 业务键：project_no / issue_no / part_no / bom_no / ecn_no / 主键 */
    private String objectId;
}
