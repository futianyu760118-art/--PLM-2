package com.hjgd.plm.m04.ecr.dto;

import com.hjgd.plm.m04.ecr.enums.EcrChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EcrDTO {

    private Long id;

    @NotBlank(message = "变更主题不能为空")
    private String title;

    /** 关联料号；转 ECN 时必须有值 */
    private String partNo;
    private String projectNo;

    @NotNull(message = "变更类型不能为空")
    private EcrChangeType changeType;

    @NotBlank(message = "变更原因不能为空")
    private String changeReason;

    private String changeContent;
    private String impactAssessment;
    /** URGENT / NORMAL / PLAN */
    private String urgency;
}
