package com.hjgd.plm.m04.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentAclDTO {

    /** USER / ROLE / DEPT / PROJECT_ROLE */
    @NotBlank(message = "主体类型不能为空")
    private String subjectType;

    @NotBlank(message = "主体标识不能为空")
    private String subjectId;

    private Boolean canRead = Boolean.TRUE;

    private Boolean canWrite = Boolean.FALSE;

    private Boolean canCheckout = Boolean.FALSE;

    private Boolean canDelete = Boolean.FALSE;
}
