package com.hjgd.plm.gate.domain;

/**
 * Gate 判定四态（F08 / ISS-004）。
 *
 * <p>核心不变量：{@link #UNKNOWN} 与 {@link #PENDING} <b>都不是</b> {@link #PASS}。
 * 输入缺失、规则未配置、Gate 未注册一律为 UNKNOWN —— 不得因为「查不到问题」
 * 就把未判定的门当成已放行。
 */
public enum GateStatus {

    /** 已注册但尚未判定（等待前序门/人工触发）。 */
    PENDING,
    /** 全部阻断规则通过。 */
    PASS,
    /** 存在阻断规则未通过。 */
    FAIL,
    /** 输入缺失 / 规则未配置 / Gate 未注册 —— 未判定，不得视为放行。 */
    UNKNOWN;

    public boolean isPass() {
        return this == PASS;
    }

    public boolean isBlocking() {
        return this != PASS;
    }

    public static GateStatus of(String s) {
        if (s == null) return UNKNOWN;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
