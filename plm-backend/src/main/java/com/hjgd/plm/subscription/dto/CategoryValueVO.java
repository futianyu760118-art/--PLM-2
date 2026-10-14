package com.hjgd.plm.subscription.dto;

import lombok.Data;

/** 分类值（选择项）。停用值仍会返回给历史标签展示，只是不进入新建分类的可选项（AC-B1-4） */
@Data
public class CategoryValueVO {

    private Long id;
    private String valueName;
    private String status;
    private Integer sortOrder;
    /** 已使用该值的资料条数，用于维度删除时提示影响范围（AC-B1-7） */
    private Long usedCount;
}
