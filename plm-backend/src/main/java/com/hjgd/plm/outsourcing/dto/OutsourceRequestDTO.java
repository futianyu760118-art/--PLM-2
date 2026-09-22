package com.hjgd.plm.outsourcing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OutsourceRequestDTO {

    private Long id;

    @NotBlank(message = "外协单位不能为空")
    private String outsourceCompany;

    private String contactPerson;
    private String purpose;
    private String drawingType;

    @NotNull(message = "有效期不能为空")
    private Integer validityDays;

    private String description;
    private List<Long> fileIds;
}
