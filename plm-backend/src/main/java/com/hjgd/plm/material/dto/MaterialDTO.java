package com.hjgd.plm.material.dto;

import com.hjgd.plm.material.enums.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialDTO {

    private Long id;
    /** 为空则 CodeGen 自动生成；手工号仅管理员可覆盖（服务内校验） */
    private String partNo;

    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    private String nameEn;

    @NotNull(message = "物料类型不能为空")
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
    private Integer makeType;
    private String unit;
    private String phase;
    private String ipRating;
    private String powerW;
    private String remark;
    /** 多阶梯供应商(1=主供 2=备选 3=试产) */
    private java.util.List<MaterialSupplierDTO> suppliers;
    /** 图纸编号(填入后自动同步到规格描述) */
    private String drawingNo;
    /** 图纸版本号 */
    private String drawingRevision;
}
