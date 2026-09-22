package com.hjgd.plm.lifecycle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通用生命周期流转请求 (docs/plm-api-spec-v1.1.md §9)。
 */
@Data
public class LifecycleTransitionDTO {

    /** PART / BOM */
    @NotBlank(message = "objectType 必填")
    private String objectType;

    /** 业务主键: PART=料号, BOM=BOM编号(或顶级料号) */
    @NotBlank(message = "objectId 必填")
    private String objectId;

    /** 动作码, 取值见 plm_lifecycle_transition.action_code */
    @NotBlank(message = "action 必填")
    private String action;

    private String comment;

    /** 预留: 覆盖守卫(需 ADMIN)。当前版本仅 where-used 类守卫未实现, 传 true 会被拒绝。 */
    private Boolean force;
}
