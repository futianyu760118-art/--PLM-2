package com.hjgd.plm.bom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BomDTO {

    private Long id;

    @NotBlank(message = "顶级成品料号不能为空")
    private String rootPartNo;

    private String versionNo;
    private String bomType;
    private String remark;
}
