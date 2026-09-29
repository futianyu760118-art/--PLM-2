package com.hjgd.plm.gate.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次 Gate 判定的完整结果（Node 放行与 G0-G8 阶段门通用）。
 *
 * @param gateCode    门编码
 * @param gateName    门名称
 * @param objectType  被判定对象类型
 * @param objectId    被判定对象 id
 * @param status      四态判定结果（UNKNOWN ≠ PASS）
 * @param outcomes    逐条规则结果（含通过项，便于复核）
 * @param failedRules 未通过 / 无法判定的规则（作为放行判定证据留痕）
 * @param reason      结论说明
 * @param gateVersion 门定义版本（判定依据可追溯）
 * @param evaluatedBy 判定人
 * @param evaluatedAt 判定时间
 */
public record GateResult(
        String gateCode,
        String gateName,
        String objectType,
        String objectId,
        GateStatus status,
        List<RuleOutcome> outcomes,
        List<RuleOutcome> failedRules,
        String reason,
        String gateVersion,
        String evaluatedBy,
        LocalDateTime evaluatedAt) {

    public boolean passed() {
        return status == GateStatus.PASS;
    }
}
