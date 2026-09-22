package com.hjgd.plm.dq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.service.ImproveService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.1 DQ 自动智能修复服务。
 *
 * 闭环: 原始债务(plm_dq_debt) → 读取 auto_fix_strategy → 尝试修复 → 写 plm_dq_fix_attempt
 *      → 重跑该料号 DQ → 若该规则 PASS 且总分提升 → 标 debt.closed_at = NOW(),waived_by='AUTO_FIX'
 *      → 失败或 NO_OP → 创建 plm_issue 升级为正式改善单,通过 linked_issue_id 关联回原始债务
 *
 * 策略类型:
 *   FILL_DEFAULT    : 用 defaultValue 填充 field 字段
 *   TRIM_WS         : 去除 field 字段首尾空格
 *   MARK_OBSOLETE   : 标记料号为 OBSOLETE
 *   LINK_TO_ISSUE   : 不修改主数据,直接升级为 Issue
 *   NO_OP           : 不修复,只创建 issue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DqAutoFixService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MaterialService materialService;
    private final DataQualityService dataQualityService;
    private final ImproveService improveService;

    /**
     * 对一条 OPEN 债务尝试自动修复。
     * @return FixAttempt 摘要(包含是否真正解决)
     */
    @Transactional
    public FixAttemptResult tryFix(Long debtId, String attemptedBy) {
        Map<String, Object> debt = jdbcTemplate.queryForMap(
                "SELECT id, object_type, object_id, rule_code, severity, status FROM plm_dq_debt WHERE id=?", debtId);
        if (!"OPEN".equals(String.valueOf(debt.get("status")))) {
            return FixAttemptResult.skipped(debtId, "DEBT_NOT_OPEN");
        }
        String objectType = String.valueOf(debt.get("object_type"));
        String objectId = String.valueOf(debt.get("object_id"));
        String ruleCode = String.valueOf(debt.get("rule_code"));
        String severity = String.valueOf(debt.get("severity"));

        if (!"PART".equals(objectType)) {
            // 当前仅 PART 支持自动修复
            return FixAttemptResult.skipped(debtId, "UNSUPPORTED_OBJECT_TYPE");
        }

        // 修复前分数
        BigDecimal beforeScore = currentScore(objectType, objectId);

        // 加载规则策略
        Map<String, Object> ruleRow;
        try {
            ruleRow = jdbcTemplate.queryForMap(
                    "SELECT rule_code, auto_fix_strategy FROM plm_dq_rule WHERE rule_code=? AND enabled=true", ruleCode);
        } catch (Exception e) {
            return FixAttemptResult.skipped(debtId, "RULE_DISABLED");
        }
        String strategyJson = ruleRow.get("auto_fix_strategy") == null ? null : String.valueOf(ruleRow.get("auto_fix_strategy"));
        if (strategyJson == null || "null".equals(strategyJson)) {
            return FixAttemptResult.skipped(debtId, "NO_STRATEGY");
        }

        // 加载物料
        Material m = materialService.getByPartNo(objectId);
        if (m == null) {
            m = tryGetById(objectId);
        }
        if (m == null) {
            return FixAttemptResult.skipped(debtId, "PART_NOT_FOUND");
        }
        Map<String, Object> beforeState = snapshot(m);

        // 执行策略
        String strategy = null;
        Long linkedIssueId = null;
        String skipReason = null;
        try {
            JsonNode strategyNode = objectMapper.readTree(strategyJson);
            strategy = text(strategyNode, "strategy");
            if (strategy == null) skipReason = "NO_STRATEGY_KEY";

            if (strategy != null) {
                switch (strategy) {
                    case "FILL_DEFAULT": doFillDefault(m, strategyNode); break;
                    case "TRIM_WS":      doTrimWs(m, strategyNode); break;
                    case "MARK_OBSOLETE":doMarkObsolete(m); break;
                    case "LINK_TO_ISSUE":linkedIssueId = doLinkToIssue(ruleCode, objectId, severity, strategyNode); break;
                    case "NO_OP":
                        // WARN/BLOCK 升级为正式改善单跟踪,INFO 直接跳过(无需人工)
                        if ("WARN".equalsIgnoreCase(severity) || "BLOCK".equalsIgnoreCase(severity)) {
                            linkedIssueId = doLinkToIssue(ruleCode, objectId, severity,
                                    objectMapper.createObjectNode().put("strategy", "LINK_TO_ISSUE")
                                            .put("title", "DQ无法自动修复: " + ruleCode)
                                            .put("severity", severity));
                        }
                        skipReason = "NO_OP";
                        break;
                    default:
                        skipReason = "UNKNOWN_STRATEGY";
                        break;
                }
            }
        } catch (Exception e) {
            log.warn("[DqAutoFix] failed debt={}: {} - {}", debtId, e.getClass().getSimpleName(), e.getMessage(), e);
            return record(debtId, ruleCode, objectType, objectId, "ERROR", beforeState, null, beforeScore, beforeScore,
                    false, null, e.getMessage(), attemptedBy);
        }

        // 修复后再跑该料号 DQ,取分
        DqRunResult reRun = dataQualityService.runForPart(m, "AUTO_FIX");
        BigDecimal afterScore = BigDecimal.valueOf(reRun.getScore());
        boolean resolved = reRun.getScore() >= 80
                && reRun.getItems().stream().noneMatch(i ->
                        ruleCode.equals(i.getRuleCode()) && "FAIL".equals(i.getResult()));

        // 记录 attempt(含 NO_OP / LINK_TO_ISSUE 全路径,作为闭环审计)
        Long attemptId = recordAttempt(debtId, ruleCode, objectType, objectId, strategy,
                beforeState, snapshot(m), beforeScore, afterScore, resolved, linkedIssueId, null, attemptedBy);

        // 解决 → 关闭原始债务
        if (resolved) {
            jdbcTemplate.update(
                    "UPDATE plm_dq_debt SET status='CLOSED', closed_at=NOW(), waive_reason=? WHERE id=?",
                    "AUTO_FIX resolved via " + strategy + " (attempt_id=" + attemptId + ")",
                    debtId);
        } else if (linkedIssueId != null) {
            // 未解决但升级为正式 issue → 在 debt 留痕,标记跟进负责人
            jdbcTemplate.update(
                    "UPDATE plm_dq_debt SET waive_reason=? WHERE id=? AND status='OPEN'",
                    "AUTO_LINK_TO_ISSUE issue_id=" + linkedIssueId + " (attempt_id=" + attemptId + ")",
                    debtId);
        }

        FixAttemptResult r = FixAttemptResult.of(debtId, ruleCode, strategy, resolved, beforeScore, afterScore, attemptId);
        r.skipReason = skipReason;
        return r;
    }

    /** 批量尝试 TOP N 债务的修复 */
    public int fixTopDebts(int limit, String attemptedBy) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM plm_dq_debt WHERE status='OPEN' ORDER BY severity DESC, created_at ASC LIMIT ?", limit);
        int ok = 0;
        for (Map<String, Object> r : rows) {
            try {
                FixAttemptResult res = tryFix(((Number) r.get("id")).longValue(), attemptedBy);
                if (res.resolved) ok++;
            } catch (Exception e) {
                log.warn("[DqAutoFix] batch debt={} failed: {}", r.get("id"), e.getMessage());
            }
        }
        return ok;
    }

    // ============ 策略实现 ============

    private void doFillDefault(Material m, JsonNode node) {
        String field = text(node, "field");
        String defaultValue = text(node, "defaultValue");
        if (field == null || defaultValue == null) return;
        applyField(m, field, defaultValue);
        materialService.updateQuietly(m);
    }

    private void doTrimWs(Material m, JsonNode node) {
        String field = text(node, "field");
        if (field == null) return;
        String cur = stringOf(m, field);
        if (cur != null) applyField(m, field, cur.trim());
        materialService.updateQuietly(m);
    }

    private void doMarkObsolete(Material m) {
        m.setStatus(com.hjgd.plm.material.enums.MaterialStatus.OBSOLETE);
        materialService.updateQuietly(m);
    }

    private Long doLinkToIssue(String ruleCode, String partNo, String severity, JsonNode node) {
        Issue issue = new Issue();
        issue.setSourceType("DQ_AUTO");
        issue.setSourceRef("DQ:" + ruleCode + "@" + partNo);
        issue.setObjectType("PART");
        issue.setObjectId(partNo);
        issue.setTitle(text(node, "title", "DQ升级: " + ruleCode));
        issue.setSeverity(severity);
        issue.setStatus("OPEN");
        issue.setCategory("QUALITY");
        issue.setDescription("DQ规则[" + ruleCode + "]无法自动修复,升级为正式改善单跟踪。");
        improveService.createIssue(issue);
        return issue.getId();
    }

    private void applyField(Material m, String field, String value) {
        switch (field) {
            case "unit":            m.setUnit(value); break;
            case "versionNo":       m.setVersionNo(value); break;
            case "specification":   m.setSpecification(value); break;
            case "nameEn":          m.setNameEn(value); break;
            case "nameZh":
            case "materialName":    m.setMaterialName(value); break;
            case "supplierCode":    m.setSupplierCode(value); break;
            case "projectNo":       m.setProjectNo(value); break;
            case "phase":           m.setPhase(value); break;
            case "remark":          m.setRemark(value); break;
            case "status":          m.setStatus(com.hjgd.plm.material.enums.MaterialStatus.valueOf(value)); break;
            default:                log.debug("[DqAutoFix] unsupported field={}", field);
        }
    }

    private String stringOf(Material m, String field) {
        return switch (field) {
            case "unit" -> m.getUnit();
            case "versionNo" -> m.getVersionNo();
            case "specification" -> m.getSpecification();
            case "nameEn" -> m.getNameEn();
            case "materialName", "nameZh" -> m.getMaterialName();
            case "supplierCode" -> m.getSupplierCode();
            case "projectNo" -> m.getProjectNo();
            case "phase" -> m.getPhase();
            case "remark" -> m.getRemark();
            default -> null;
        };
    }

    private Map<String, Object> snapshot(Material m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", m.getId());
        s.put("partNo", m.getPartNo());
        s.put("materialName", m.getMaterialName());
        s.put("unit", m.getUnit());
        s.put("versionNo", m.getVersionNo());
        s.put("specification", m.getSpecification());
        s.put("ipRating", m.getIpRating());
        s.put("powerW", m.getPowerW());
        s.put("status", m.getStatus() == null ? null : m.getStatus().name());
        return s;
    }

    private BigDecimal currentScore(String objectType, String objectId) {
        try {
            BigDecimal s = jdbcTemplate.queryForObject(
                    "SELECT score_0_100 FROM plm_dq_object_score WHERE object_type=? AND object_id=?",
                    BigDecimal.class, objectType, objectId);
            return s == null ? BigDecimal.ZERO : s;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Long recordAttempt(Long debtId, String ruleCode, String objectType, String objectId, String strategy,
                                Map<String, Object> before, Map<String, Object> after,
                                BigDecimal beforeScore, BigDecimal afterScore,
                                boolean resolved, Long linkedIssueId, String errorMsg, String attemptedBy) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO plm_dq_fix_attempt(source_debt_id, rule_code, object_type, object_id, strategy, " +
                            "before_state, after_state, before_score, after_score, resolved, linked_issue_id, " +
                            "error_msg, attempted_by, confirmed_at) " +
                            "VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?,?)",
                    debtId, ruleCode, objectType, objectId, strategy,
                    toJson(before), toJson(after),
                    beforeScore, afterScore, resolved, linkedIssueId,
                    errorMsg, attemptedBy,
                    resolved ? OffsetDateTime.now() : null);
            return jdbcTemplate.queryForObject("SELECT currval('plm_dq_fix_attempt_id_seq')", Long.class);
        } catch (Exception e) {
            log.warn("[DqAutoFix] record attempt failed: {}", e.getMessage());
            return null;
        }
    }

    private FixAttemptResult record(Long debtId, String ruleCode, String objectType, String objectId,
                                    String strategy, Map<String, Object> before, Map<String, Object> after,
                                    BigDecimal beforeScore, BigDecimal afterScore, boolean resolved,
                                    Long linkedIssueId, String errorMsg, String attemptedBy) {
        Long id = recordAttempt(debtId, ruleCode, objectType, objectId, strategy, before, after,
                beforeScore, afterScore, resolved, linkedIssueId, errorMsg, attemptedBy);
        return FixAttemptResult.of(debtId, ruleCode, strategy, resolved, beforeScore, afterScore, id);
    }

    private Material tryGetById(String idOrPartNo) {
        try {
            return materialService.getById(Long.parseLong(idOrPartNo));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        return text(n, field, null);
    }
    private static String text(JsonNode n, String field, String def) {
        return n == null || n.get(field) == null || n.get(field).isNull() ? def : n.get(field).asText();
    }

    private String toJson(Map<String, Object> m) {
        try { return objectMapper.writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }

    public static class FixAttemptResult {
        public final Long debtId;
        public final String ruleCode;
        public final String strategy;
        public final boolean resolved;
        public final BigDecimal beforeScore;
        public final BigDecimal afterScore;
        public final Long attemptId;
        public String skipReason;
        FixAttemptResult(Long d, String r, String s, boolean ok, BigDecimal b, BigDecimal a, Long aid, String skip) {
            this.debtId = d; this.ruleCode = r; this.strategy = s; this.resolved = ok;
            this.beforeScore = b; this.afterScore = a; this.attemptId = aid; this.skipReason = skip;
        }
        public static FixAttemptResult of(Long d, String r, String s, boolean ok, BigDecimal b, BigDecimal a, Long aid) {
            return new FixAttemptResult(d, r, s, ok,
                    b == null ? BigDecimal.ZERO : b,
                    a == null ? BigDecimal.ZERO : a,
                    aid, null);
        }
        public static FixAttemptResult skipped(Long d, String reason) {
            return new FixAttemptResult(d, null, null, false, BigDecimal.ZERO, BigDecimal.ZERO, null, reason);
        }
    }
}