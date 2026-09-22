package com.hjgd.plm.lifecycle.service;

/**
 * 生命周期通用流转入口 (v5 §4.1): 转换 = 守卫 + 动作 + 事件, 单事务。
 *
 * <p>PART/BOM 的既有 actions/* 接口与 {@code POST /v1/lifecycle/transition} 走同一套守卫,
 * 矩阵来自 plm_lifecycle_transition (见 LifecycleService)。DQ 门禁由动作实现内部执行,
 * 保证 REST 动作接口与通用入口行为一致, 不做二次 DQ。
 */
public interface LifecycleTransitionService {

    /**
     * @param objectType PART / BOM
     * @param objectId   PART=料号, BOM=BOM编号或顶级料号
     * @param action     动作码 (plm_lifecycle_transition.action_code)
     * @param force      覆盖守卫(仅 ADMIN); 当前版本未实现, 传 true 被拒
     * @return 流转结果(objectId/from/to/action)
     */
    TransitionResult transition(String objectType, String objectId, String action, String comment, boolean force);

    record TransitionResult(String objectType, String objectId, String fromState, String toState,
                            String action) {
    }
}
