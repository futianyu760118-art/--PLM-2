package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.util.List;

/** 分类维度（含其分类值）。{@code editable=false} 时前端不显示编辑入口（AC-B2-1/2/5/6） */
@Data
public class CategoryDimVO {

    private Long id;
    private String dimName;
    private Long deptId;
    /** 全局维度：全部部门只读可见，任何部门管理员不可改（AC-B2-6） */
    private Boolean global;
    /** 共享维度：他部门可选值但不可改定义（AC-B2-5） */
    private Boolean shared;
    private String status;
    private Integer sortOrder;
    /** 当前用户对本人可管理范围内的维度为 true；全局维度与他部门维度为 false */
    private Boolean editable;
    /** 已使用该维度的资料条数（AC-B1-7 删除影响提示） */
    private Long usedCount;
    private List<CategoryValueVO> values;
}
