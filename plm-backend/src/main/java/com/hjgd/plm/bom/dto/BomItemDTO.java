package com.hjgd.plm.bom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomItemDTO {

    private Long id;
    private Long bomId;

    private Long parentItemId;

    @NotBlank(message = "子件料号不能为空")
    private String partNo;

    private String partName;

    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;

    private String materialTexture;
    private String specification;
    private String unit;
    private Integer makeType;
    private String versionNo;
    private Long model3dFileId;
    private Long drawingFileId;
    private Integer sortOrder;
    private String remark;
}
