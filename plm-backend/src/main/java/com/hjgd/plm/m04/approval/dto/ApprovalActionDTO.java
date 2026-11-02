package com.hjgd.plm.m04.approval.dto;

import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批动作入参。
 */
@Data
public class ApprovalActionDTO {

    @NotNull(message = "审批动作不能为空")
    private ApprovalAction action;

    private String comment;
}
