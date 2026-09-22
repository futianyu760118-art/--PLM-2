package com.hjgd.plm.ecn.dto;

import com.hjgd.plm.ecn.enums.EcnChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EcnDTO {

    private Long id;
    private String ecnNo;

    @NotBlank(message = "关联物料料号不能为空")
    private String partNo;

    @NotNull(message = "变更类型不能为空")
    private EcnChangeType changeType;

    private String versionBefore;
    private String versionAfter;
    private String changeLocation;

    @NotBlank(message = "变更原因不能为空")
    private String changeReason;

    private BigDecimal moldCost;
    private String impactScope;
    private String trialImpact;
    private String massImpact;
}
