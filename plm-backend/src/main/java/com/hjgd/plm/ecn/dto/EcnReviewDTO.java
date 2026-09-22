package com.hjgd.plm.ecn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EcnReviewDTO {

    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
