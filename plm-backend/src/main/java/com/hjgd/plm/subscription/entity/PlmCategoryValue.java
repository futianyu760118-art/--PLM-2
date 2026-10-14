package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 分类维度值（AC-B1-1/B1-3/B1-4）。
 *
 * <p>停用后不再出现在新的分类选择项中，但历史已打该值的资料标签保留（AC-B1-4）。
 */
@Data
@TableName("plm_category_value")
public class PlmCategoryValue extends BaseEntity {

    private Long dimId;
    private String valueCode;
    /** 维度值 ≤30（AC-B1-1），同维度内唯一 */
    private String valueName;
    /** ENABLED / DISABLED */
    private String status;
    private Integer sortOrder;
}
