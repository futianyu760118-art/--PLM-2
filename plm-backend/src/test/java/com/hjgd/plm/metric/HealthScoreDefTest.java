package com.hjgd.plm.metric;

import com.hjgd.plm.metric.domain.HealthScoreDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F10 指标口径：定义式与实现值的唯一来源。
 *
 * <p>验收口径是「对同一数据集，定义式计算值与实现值偏差 = 0」——
 * 因此这里直接按定义式手算期望值，逐项比对。
 */
@DisplayName("F10 健康分口径(HealthScoreDef)")
class HealthScoreDefTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("节点完成率/关键节点完成率: 空分母按 0")
    void completionRates() {
        assertEquals(0.5, HealthScoreDef.nodeCompletionRate(11, 22), EPS);
        assertEquals(0.0, HealthScoreDef.nodeCompletionRate(0, 0), EPS);
        assertEquals(1.0, HealthScoreDef.keyCompletionRate(7, 7), EPS);
        assertEquals(0.0, HealthScoreDef.keyCompletionRate(3, 0), EPS);
    }

    @Test
    @DisplayName("准时率: 无带日期完成项时按 1.0(无逾期即满分)")
    void onTimeRate() {
        assertEquals(0.5, HealthScoreDef.onTimeRate(1, 2), EPS);
        assertEquals(1.0, HealthScoreDef.onTimeRate(0, 0), EPS);
    }

    @Test
    @DisplayName("必填字段完整率: 空分母按 0")
    void fieldCompleteness() {
        assertEquals(0.8, HealthScoreDef.fieldCompleteness(8, 10), EPS);
        assertEquals(0.0, HealthScoreDef.fieldCompleteness(0, 0), EPS);
    }

    @Test
    @DisplayName("变更因子: 无变更满分, 达到上限归零, 超额不为负")
    void changeFactor() {
        assertEquals(1.0, HealthScoreDef.changeFactor(0), EPS);
        assertEquals(1.0, HealthScoreDef.changeFactor(-3), EPS);
        assertEquals(0.5, HealthScoreDef.changeFactor(25), EPS);
        assertEquals(0.0, HealthScoreDef.changeFactor(HealthScoreDef.CHANGE_FACTOR_CEILING), EPS);
        assertEquals(0.0, HealthScoreDef.changeFactor(999), EPS);
    }

    @Test
    @DisplayName("健康分 = 100*(0.35*ncr+0.25*kcr+0.20*otr+0.10*cr+0.10*cf), 裁剪到 [0,100]")
    void healthMatchesDefinitionExactly() {
        double ncr = 11.0 / 22, kcr = 7.0 / 7, otr = 3.0 / 5, cr = 8.0 / 10, cf = 0.5;
        double expected = 100 * (0.35 * ncr + 0.25 * kcr + 0.20 * otr + 0.10 * cr + 0.10 * cf);
        assertEquals((int) Math.round(expected), HealthScoreDef.health(ncr, kcr, otr, cr, cf));

        // 权重之和为 1 → 全满分 = 100，全 0 = 0
        assertEquals(100, HealthScoreDef.health(1, 1, 1, 1, 1));
        assertEquals(0, HealthScoreDef.health(0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("权重之和为 1.0（口径自洽）")
    void weightsSumToOne() {
        double sum = HealthScoreDef.W_NODE_COMPLETION + HealthScoreDef.W_KEY_COMPLETION
                + HealthScoreDef.W_ON_TIME + HealthScoreDef.W_FIELD_COMPLETENESS
                + HealthScoreDef.W_CHANGE_FACTOR;
        assertEquals(1.0, sum, EPS);
    }

    @Test
    @DisplayName("变更分类取值集完整: 正式/派生/录入均可解析, 未知返回 null")
    void changeCategoryCoverage() {
        assertTrue(HealthScoreDef.ChangeCategory.fromAction("CREATE").isFormal());
        assertTrue(HealthScoreDef.ChangeCategory.fromAction("update").isFormal());
        assertTrue(HealthScoreDef.ChangeCategory.fromAction("DELETE").isFormal());
        assertFalse(HealthScoreDef.ChangeCategory.fromAction("ROLLUP").isFormal());
        assertFalse(HealthScoreDef.ChangeCategory.fromAction("CELL").isFormal());
        assertNull(HealthScoreDef.ChangeCategory.fromAction("UNKNOWN_ACTION"));
        assertNull(HealthScoreDef.ChangeCategory.fromAction(null));
    }

    @Test
    @DisplayName("必填项适用规则: 关键项/完成项才计入分母")
    void requiredFieldApplicability() {
        // 非关键、未完成: 仅 STATUS_SET / NODE_NAME / SEQ
        assertEquals(3, HealthScoreDef.requiredFieldCount(false, false));
        // 非关键、已完成: + ACTUAL_DATE / EVIDENCE
        assertEquals(5, HealthScoreDef.requiredFieldCount(false, true));
        // 关键、未完成: + PLAN_DATE / OWNER
        assertEquals(5, HealthScoreDef.requiredFieldCount(true, false));
        // 关键、已完成: 全部 7 项
        assertEquals(7, HealthScoreDef.requiredFieldCount(true, true));

        assertTrue(HealthScoreDef.RequiredField.PLAN_DATE.appliesTo(true, false));
        assertFalse(HealthScoreDef.RequiredField.PLAN_DATE.appliesTo(false, false));
        assertTrue(HealthScoreDef.RequiredField.EVIDENCE.appliesTo(false, true));
        assertFalse(HealthScoreDef.RequiredField.EVIDENCE.appliesTo(false, false));
    }

    @Test
    @DisplayName("保留 3 位小数与既有对外口径一致")
    void rounding() {
        assertEquals(0.333, HealthScoreDef.round3(1.0 / 3), EPS);
        assertEquals(0.667, HealthScoreDef.round3(2.0 / 3), EPS);
    }

    @Test
    @DisplayName("采集方法可追溯(写入 plm_metric_object.baseline_method)")
    void collectionMethodDocumented() {
        assertNotNull(HealthScoreDef.COLLECTION_METHOD);
        assertTrue(HealthScoreDef.COLLECTION_METHOD.contains("plm_project_change_log"));
        assertTrue(HealthScoreDef.COLLECTION_METHOD.contains("'CREATE','UPDATE','DELETE'"));
    }
}
