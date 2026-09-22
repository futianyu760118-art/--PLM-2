package com.hjgd.plm.improve.service;

import com.hjgd.plm.improve.entity.Insight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 洞察生成 Job：聚合 DQ 夜检产出的债务(plm_dq_debt)，按规则汇总成 TOP 洞察。
 * - 每条规则 → 一条 plm_analytics_insight(幂等：同标题 NEW 洞察已存在则跳过)
 * - HIGH 严重(债务条数≥阈值) → 自动转 plm_issue(验收: DQ夜检→自动开ISSUE→看板可见)
 *
 * 属于规则引擎，非 LLM 智能体自治；Agent L0 只读消费这些洞察。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final ImproveService improveService;

    /** 债务条数 ≥ 该阈值 → HIGH(自动开 ISSUE) */
    static final int HIGH_THRESHOLD = 5;
    /** 债务条数 ≥ 该阈值 → MEDIUM */
    static final int MEDIUM_THRESHOLD = 3;
    private static final int TOP_LIMIT = 5;

    private static final String TOP_DEBT_SQL =
            "SELECT rule_code, severity, COUNT(*) AS cnt, MIN(message) AS msg " +
                    "FROM plm_dq_debt WHERE status='OPEN' " +
                    "GROUP BY rule_code, severity ORDER BY cnt DESC LIMIT " + TOP_LIMIT;

    /** 从 DQ 债务生成洞察；返回新生成条数 */
    public int generateFromDqDebt() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(TOP_DEBT_SQL);
        } catch (Exception e) {
            log.warn("[InsightGen] load dq debt failed: {}", e.getMessage());
            return 0;
        }
        if (rows == null || rows.isEmpty()) {
            log.info("[InsightGen] 无 OPEN 债务，跳过洞察生成");
            return 0;
        }

        int generated = 0;
        for (Map<String, Object> row : rows) {
            String rule = str(row.get("rule_code"));
            String debtSev = str(row.get("severity"));
            int cnt = intVal(row.get("cnt"));
            String msg = str(row.get("msg"));
            if (rule == null || rule.isBlank()) {
                continue;
            }

            String title = "DQ债务: " + rule + " (" + cnt + "条" + (debtSev == null ? "" : debtSev) + ")";
            if (existsNewInsight(title)) {
                continue; // 幂等：同标题 NEW 洞察已存在，不重复
            }

            String insightSev = severityOf(cnt, debtSev);
            Insight insight = new Insight();
            insight.setSource("DQ_SCAN");
            insight.setTitle(title);
            insight.setSeverity(insightSev);
            insight.setCategory("QUALITY");
            insight.setFindingJson(toJson(Map.of(
                    "ruleCode", rule, "severity", debtSev == null ? "" : debtSev,
                    "count", cnt, "sample", msg == null ? "" : msg)));
            insight.setRecommendationJson(toJson(Map.of("hint", msg == null ? "建议补全该字段" : msg)));
            insight.setRelatedKpiCodes("KPI_DQ_SCORE");
            try {
                improveService.createInsight(insight);
                generated++;
            } catch (Exception e) {
                log.warn("[InsightGen] createInsight failed rule={}, skip: {}", rule, e.getMessage());
                continue;
            }

            // HIGH → 自动转 ISSUE(验收: 自动开 ISSUE → 早会看板可见)
            if ("HIGH".equals(insightSev)) {
                try {
                    improveService.convertInsightToIssue(insight.getId());
                    log.info("[InsightGen] HIGH 洞察[{}]自动转 ISSUE: {}", insight.getInsightNo(), title);
                } catch (Exception e) {
                    log.warn("[InsightGen] convertInsightToIssue failed insight={}: {}", insight.getId(), e.getMessage());
                }
            }
        }
        log.info("[InsightGen] 生成洞察 {} 条(共 {} 条债务规则)", generated, rows.size());
        return generated;
    }

    /** 债务严重度 → 洞察严重度(纯函数,可单测) */
    static String severityOf(int count, String debtSeverity) {
        if (count >= HIGH_THRESHOLD || "WARN".equalsIgnoreCase(debtSeverity)) {
            return "HIGH";
        }
        if (count >= MEDIUM_THRESHOLD) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean existsNewInsight(String title) {
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM plm_analytics_insight WHERE title=? AND status='NEW'",
                    Integer.class, title);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    private static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> e : new LinkedHashMap<>(m).entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) sb.append(v);
            else sb.append("\"").append(String.valueOf(v).replace("\"", "'")).append("\"");
        }
        return sb.append("}").toString();
    }
}
