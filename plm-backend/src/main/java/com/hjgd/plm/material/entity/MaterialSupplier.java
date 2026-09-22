package com.hjgd.plm.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 物料-供应商阶梯实体(主供/备选/试产)。
 */
@Data
@TableName("plm_material_supplier")
public class MaterialSupplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String supplierCode;
    private String supplierName;
    private Integer tierRank;
    private BigDecimal price;
    private String currency;
    private BigDecimal sharePct;
    private Integer leadTimeDays;
    private Integer moq;
    private String remark;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}