package com.hjgd.plm.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料-供应商阶梯 DTO。tierRank=1 主供,2 备选,3 试产。
 */
@Data
public class MaterialSupplierDTO {
    private Long id;
    private Long materialId;
    @NotBlank private String supplierCode;
    @NotBlank private String supplierName;
    @NotNull private Integer tierRank;
    private BigDecimal price;
    private String currency;
    private BigDecimal sharePct;
    private Integer leadTimeDays;
    private Integer moq;
    private String remark;
    private Boolean enabled;
}