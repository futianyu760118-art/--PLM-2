package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.util.List;

/** 新增/编辑分类维度（AC-B1-1：维度名称 ≤ 20 字，维度值每项 ≤ 30 字） */
@Data
public class CategoryDimRequest {

    private String dimName;
    /** 新增时随维度一次提交的维度值列表（可空，之后再用分类值接口追加） */
    private List<String> values;
    /** 是否共享给他部门（AC-B2-5）；不共享则仅本部门可见（AC-B2-4） */
    private Boolean shared;
    private Integer sortOrder;
}
