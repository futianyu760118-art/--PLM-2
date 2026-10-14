package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 分类维度（AC-B1 / AC-B2）。
 *
 * <p>{@code deptId} 为 NULL 且 {@code isGlobal=1} 表示系统预置的全局维度，
 * 对全部部门只读可见（AC-B2-6）；{@code isShared=1} 表示他部门可选该维度值但不可改定义（AC-B2-5）。
 */
@Data
@TableName("plm_category_dim")
public class PlmCategoryDim extends BaseEntity {

    /** 归属部门；NULL = 全局维度 */
    private Long deptId;
    private String dimCode;
    /** 维度名称 ≤20，本部门内唯一（AC-B1-1/B1-5） */
    private String dimName;
    /** 1=全局预置只读（AC-B2-6） */
    private Integer isGlobal;
    /** 1=共享给他部门选用（AC-B2-4/B2-5） */
    private Integer isShared;
    /** ENABLED / DISABLED */
    private String status;
    private Integer sortOrder;
    private Long createdBy;
}
