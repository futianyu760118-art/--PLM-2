package com.hjgd.plm.quality.dto;

import com.hjgd.plm.quality.enums.InspectionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionStandardDTO {

    private Long id;

    @NotBlank(message = "关联料号不能为空")
    private String partNo;

    @NotNull(message = "检验分类不能为空")
    private InspectionCategory category;

    @NotBlank(message = "检验项目不能为空")
    private String itemName;

    private String standardValue;
    private String toleranceRange;
    private String tool;
    private String method;
    private String criteria;
    private String defectDef;
    private String model3dPosition;
    private String drawingVersion;
}
