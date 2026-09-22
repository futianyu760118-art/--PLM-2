package com.hjgd.plm.material.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.enums.MaterialType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plm_material")
public class Material extends BaseEntity {

    private String partNo;
    private String materialName;
    private String nameEn;
    @EnumValue
    private MaterialType materialType;
    private String partCategory;
    private String productType;
    private String materialTexture;
    private String color;
    private String specification;
    private String productSeries;
    private String projectNo;
    private String supplierCode;
    private String supplierName;
    /**
     * 唯一生命周期状态。列名 lifecycle_state —— 双轨字段 status/lifecycle_status
     * 已由 database/26_v5_unify_lifecycle_state.sql 合并, 属性名保留 status 以免
     * 破坏 /query、前端 row.status 等既有调用方。
     */
    @EnumValue
    @TableField("lifecycle_state")
    private MaterialStatus status;
    private String versionNo;
    private Integer makeType;
    private String unit;
    private String phase;
    private String ipRating;
    private String powerW;
    private BigDecimal standardCost;
    private String costCurrency;
    private String createdBy;
    private String remark;
    /** 图纸编号(填入后自动同步到规格描述) */
    private String drawingNo;
    /** 图纸版本号(变更时自动更新描述: 规格 | 图纸 DX202401 V2.0) */
    private String drawingRevision;
}
