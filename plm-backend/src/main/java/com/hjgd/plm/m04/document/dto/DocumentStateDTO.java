package com.hjgd.plm.m04.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentStateDTO {

    /** 目标状态：RELEASED / ARCHIVED / OBSOLETE / RECYCLE / WORKING */
    @NotBlank(message = "目标状态不能为空")
    private String state;

    private String remark;
}
