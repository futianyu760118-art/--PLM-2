package com.hjgd.plm.gate.domain;

/**
 * 单条 Gate 规则的判定结果。
 *
 * <p>三态：通过 / 未通过 / 无法判定。无法判定用于「输入缺失」——
 * 它与「未通过」不同，但同样不构成放行依据，会把整个 Gate 拉到
 * {@link GateStatus#UNKNOWN}。
 */
public record RuleOutcome(
        Long ruleId,
        GateRuleType ruleType,
        String ruleKey,
        GateStatus status,
        String severity,
        String reason) {

    public static RuleOutcome pass(Long ruleId, GateRuleType type, String key, String severity, String reason) {
        return new RuleOutcome(ruleId, type, key, GateStatus.PASS, severity, reason);
    }

    public static RuleOutcome fail(Long ruleId, GateRuleType type, String key, String severity, String reason) {
        return new RuleOutcome(ruleId, type, key, GateStatus.FAIL, severity, reason);
    }

    public static RuleOutcome unknown(Long ruleId, GateRuleType type, String key, String severity, String reason) {
        return new RuleOutcome(ruleId, type, key, GateStatus.UNKNOWN, severity, reason);
    }

    public boolean isBlock() {
        return "BLOCK".equalsIgnoreCase(severity);
    }
}
