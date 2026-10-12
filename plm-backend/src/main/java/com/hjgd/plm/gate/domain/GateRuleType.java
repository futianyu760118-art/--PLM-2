package com.hjgd.plm.gate.domain;

/**
 * Gate 规则类型（F08）。Node 放行与 G0-G8 阶段门共用同一套规则类型，
 * 不存在第二套平行判断逻辑（ISS-004 的修复要点）。
 */
public enum GateRuleType {

    /** 节点事实：实际完成日期、节点状态等来自 {@code plm_project_node} 的事实。 */
    NODE,
    /** 审批事实：来自 {@code plm_project_node_approval} 的双级审批。 */
    APPROVAL,
    /** 证据事实：来自统一证据底座 {@code plm_evidence}。 */
    EVIDENCE,
    /** 指标事实：来自 {@code plm_metric_value}（口径由 {@code plm_metric_object} 治理）。 */
    METRIC,
    /** 契约事实：交付物齐套，来自证据底座中的 kit 声明。 */
    CONTRACT;

    public static GateRuleType of(String s) {
        if (s == null) throw new IllegalArgumentException("rule_type 为空");
        return valueOf(s.trim().toUpperCase());
    }
}
