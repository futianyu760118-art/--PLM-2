package com.hjgd.plm.m04.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 审批流定义（含节点）。保存时整体覆盖节点，便于页面直接编辑。
 */
@Data
public class ApprovalFlowDTO {

    private Long id;
    @NotBlank(message = "流程编码不能为空")
    private String flowCode;
    @NotBlank(message = "单据类型不能为空")
    private String objectType;
    @NotBlank(message = "流程名称不能为空")
    private String name;
    private String versionNo;
    /** DRAFT / ENABLED / DISABLED */
    private String status;
    private String remark;

    @Valid
    @NotEmpty(message = "至少配置一个审批节点")
    private List<ApprovalNodeDTO> nodes;
}
