package com.hjgd.plm.subscription.dto;

import lombok.Data;

/** 分类值新增/改名/停用（AC-B1-4、AC-B1-5） */
@Data
public class CategoryValueRequest {

    private String valueName;
    /** ACTIVE 启用 / DISABLED 停用 */
    private String status;
    private Integer sortOrder;
}
