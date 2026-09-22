package com.hjgd.plm.lifecycle.service;

import com.hjgd.plm.material.enums.MaterialStatus;

import java.util.List;
import java.util.Set;

public interface LifecycleService {

    void assertTransition(String objectType, MaterialStatus from, String actionCode);

    MaterialStatus resolveToState(String objectType, MaterialStatus from, String actionCode);

    /** 引擎按 object_type 通用入口: PART/BOM/FILE... 状态以字符串表示 */
    void assertTransition(String objectType, String fromState, String actionCode);

    /** 目标状态; 非法流转抛 BusinessException(409 LIFECYCLE_DENIED) */
    String resolveToState(String objectType, String fromState, String actionCode);

    /** 转换规则允许的角色码; 规则未配置 roles 时返回空集(=不限) */
    Set<String> allowedRoles(String objectType, String fromState, String actionCode);

    /** 转换规则是否要求过 DQ 门禁 */
    boolean requiresDq(String objectType, String fromState, String actionCode);

    /** 当前用户是否满足转换规则的角色要求 */
    boolean isRoleAllowed(String objectType, String fromState, String actionCode);

    /** 某对象类型全部启用的转换规则, 供 GET /lifecycle/transitions 使用 */
    List<TransitionRule> listRules(String objectType);

    /**
     * 写转换审计历史(plm_lifecycle_history)。写失败必须让调用方失败——
     * 生命周期流转不允许「状态改了但没留痕」。
     */
    void recordHistory(String objectType, String objectId, String from, String to,
                       String actionCode, String operator, String comment);

    /** 转换规则只读视图 */
    record TransitionRule(String objectType, String fromState, String toState,
                          String actionCode, Set<String> roles, boolean requireDq) {
    }
}
