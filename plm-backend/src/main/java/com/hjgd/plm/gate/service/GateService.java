package com.hjgd.plm.gate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.service.EvidenceService;
import com.hjgd.plm.gate.domain.GateResult;
import com.hjgd.plm.gate.domain.GateRuleType;
import com.hjgd.plm.gate.domain.GateStatus;
import com.hjgd.plm.gate.domain.RuleOutcome;
import com.hjgd.plm.gate.entity.Gate;
import com.hjgd.plm.gate.entity.GateInstance;
import com.hjgd.plm.gate.entity.GateRule;
import com.hjgd.plm.gate.mapper.GateInstanceMapper;
import com.hjgd.plm.gate.mapper.GateMapper;
import com.hjgd.plm.gate.mapper.GateRuleMapper;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.NodeApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一 Gate 引擎（F08 / ISS-004）。
 *
 * <p>Node 放行与 G0-G8 阶段门走<b>同一个</b> {@link #evaluate} 入口、同一规则模型、
 * 同一四态语义。原先散落在各处「查一下证据数够不够」的判门逻辑全部收敛到这里，
 * 消除平行判断路径。
 *
 * <p>四态聚合规则（见 {@link #aggregate}）：
 * <ol>
 *   <li>任一 BLOCK 规则 FAIL → {@link GateStatus#FAIL}；</li>
 *   <li>否则任一 BLOCK 规则 UNKNOWN → {@link GateStatus#UNKNOWN}（输入缺失，不放行也不判死）；</li>
 *   <li>否则 → {@link GateStatus#PASS}，WARN 级未通过/无法判定仅作为告警记录在案。</li>
 * </ol>
 * Gate 未注册、未启用、未配置启用规则 → 一律 {@link GateStatus#UNKNOWN}，<b>绝不当成 PASS</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateService {

    /** 关键节点完成放行门。 */
    public static final String GATE_NODE_KEY_DONE = "GATE_NODE_KEY_DONE";
    /** 技转齐套门。 */
    public static final String GATE_TECH_TRANSFER = "GATE_TECH_TRANSFER";

    private static final String OBJ_NODE = "node";

    private final GateMapper gateMapper;
    private final GateRuleMapper gateRuleMapper;
    private final GateInstanceMapper gateInstanceMapper;
    private final ProjectNodeMapper nodeMapper;
    private final NodeApprovalService nodeApprovalService;
    private final EvidenceService evidenceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 公开入口

    /**
     * 判定一个门，并把本次判定写入 {@code plm_gate_instance} 留痕。
     * 这是全系统唯一的放行判定入口。
     */
    @Transactional
    public GateResult evaluate(String gateCode, String objectType, String objectId) {
        Gate gate = findGate(gateCode);
        if (gate == null) {
            return persist(GateStatus.UNKNOWN, gateCode, null, null, objectType, objectId,
                    List.of(), List.of(), List.of(), "Gate 未注册: " + gateCode, null);
        }
        if (Boolean.FALSE.equals(gate.getEnabled())) {
            return persist(GateStatus.UNKNOWN, gateCode, gate.getName(), gate.getVersion(),
                    objectType, objectId, List.of(), List.of(), List.of(),
                    "Gate 已停用: " + gateCode, gate.getVersion());
        }
        List<GateRule> rules = enabledRules(gate.getId());
        if (rules.isEmpty()) {
            return persist(GateStatus.UNKNOWN, gateCode, gate.getName(), gate.getVersion(),
                    objectType, objectId, List.of(), List.of(), List.of(),
                    "未配置启用规则，判定为 UNKNOWN（未配置不得放行）", gate.getVersion());
        }

        List<RuleOutcome> outcomes = new ArrayList<>();
        Set<String> evidenceIds = new LinkedHashSet<>();
        for (GateRule r : rules) {
            RuleEval eval = evaluateRule(r, objectType, objectId);
            outcomes.add(eval.outcome());
            evidenceIds.addAll(eval.evidenceIds());
        }
        GateStatus status = aggregate(outcomes);
        List<RuleOutcome> nonPass = outcomes.stream()
                .filter(o -> o.status() != GateStatus.PASS)
                .toList();
        String reason = buildReason(status, nonPass);
        return persist(status, gateCode, gate.getName(), gate.getVersion(), objectType, objectId,
                outcomes, nonPass, new ArrayList<>(evidenceIds), reason, gate.getVersion());
    }

    /** 判定并断言放行；未 PASS 时抛出 GATE_DENIED。 */
    public GateResult assertPassed(String gateCode, String objectType, String objectId) {
        GateResult r = evaluate(gateCode, objectType, objectId);
        if (!r.passed()) {
            throw new BusinessException(409, "[" + ApiErrorCodes.GATE_DENIED + "] 门[" + gateCode
                    + "]未放行: 状态=" + r.status() + "，原因=" + r.reason());
        }
        return r;
    }

    /** 只判定、不留痕（供界面预检 / 只读查询）。 */
    public GateResult peek(String gateCode, String objectType, String objectId) {
        Gate gate = findGate(gateCode);
        if (gate == null) {
            return new GateResult(gateCode, null, objectType, objectId, GateStatus.UNKNOWN,
                    List.of(), List.of(), "Gate 未注册: " + gateCode, null, null, null);
        }
        List<GateRule> rules = enabledRules(gate.getId());
        if (rules.isEmpty()) {
            return new GateResult(gateCode, gate.getName(), objectType, objectId, GateStatus.UNKNOWN,
                    List.of(), List.of(), "未配置启用规则，判定为 UNKNOWN（未配置不得放行）",
                    gate.getVersion(), null, null);
        }
        List<RuleOutcome> outcomes = new ArrayList<>();
        for (GateRule r : rules) {
            outcomes.add(evaluateRule(r, objectType, objectId).outcome());
        }
        GateStatus status = aggregate(outcomes);
        List<RuleOutcome> nonPass = outcomes.stream().filter(o -> o.status() != GateStatus.PASS).toList();
        return new GateResult(gateCode, gate.getName(), objectType, objectId, status, outcomes, nonPass,
                buildReason(status, nonPass), gate.getVersion(), null, null);
    }

    /** Gate 定义清单（供前端展示阶段门全貌）。 */
    public List<Map<String, Object>> catalog() {
        LambdaQueryWrapper<Gate> w = new LambdaQueryWrapper<>();
        w.orderByAsc(Gate::getSeq).orderByAsc(Gate::getId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Gate g : gateMapper.selectList(w)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gateCode", g.getGateCode());
            m.put("name", g.getName());
            m.put("seq", g.getSeq());
            m.put("ownerRole", g.getOwnerRole());
            m.put("version", g.getVersion());
            m.put("enabled", g.getEnabled());
            m.put("ruleCount", enabledRules(g.getId()).size());
            out.add(m);
        }
        return out;
    }

    /** 某对象的判定历史（时间倒序）——「已放行」的留痕证据。 */
    public List<GateInstance> history(String gateCode, String objectType, String objectId) {
        LambdaQueryWrapper<GateInstance> w = new LambdaQueryWrapper<>();
        w.eq(GateInstance::getGateCode, gateCode)
                .eq(GateInstance::getObjectType, objectType)
                .eq(GateInstance::getObjectId, objectId)
                .orderByDesc(GateInstance::getEvaluatedAt)
                .orderByDesc(GateInstance::getId);
        return gateInstanceMapper.selectList(w);
    }

    /** 最近一次判定（无则 null）。 */
    public GateInstance latestInstance(String gateCode, String objectType, String objectId) {
        List<GateInstance> list = history(gateCode, objectType, objectId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------------------------------------------------------- 规则判定

    /** 规则判定结果 + 本次判定引用到的证据 id。 */
    private record RuleEval(RuleOutcome outcome, List<String> evidenceIds) {
        static RuleEval of(RuleOutcome o) { return new RuleEval(o, List.of()); }
    }

    private RuleEval evaluateRule(GateRule rule, String objectType, String objectId) {
        GateRuleType type;
        try {
            type = GateRuleType.of(rule.getRuleType());
        } catch (Exception e) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), null, rule.getRuleKey(),
                    rule.getSeverity(), "未知规则类型: " + rule.getRuleType()));
        }
        try {
            return switch (type) {
                case NODE -> evalNode(rule, objectType, objectId);
                case APPROVAL -> evalApproval(rule, objectType, objectId);
                case EVIDENCE -> evalEvidence(rule, objectType, objectId);
                case METRIC -> evalMetric(rule, objectType, objectId);
                case CONTRACT -> evalContract(rule, objectType, objectId);
            };
        } catch (Exception e) {
            log.warn("[gate] 规则判定异常 rule={} {}: {}", rule.getId(), rule.getRuleKey(), e.getMessage());
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), type, rule.getRuleKey(),
                    rule.getSeverity(), "规则判定异常: " + e.getMessage()));
        }
    }

    private RuleEval evalNode(GateRule rule, String objectType, String objectId) {
        if (!OBJ_NODE.equals(objectType)) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                    rule.getSeverity(), "NODE 规则仅适用于 object_type=node，当前 " + objectType));
        }
        Long nodeId = parseId(objectId);
        if (nodeId == null) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                    rule.getSeverity(), "节点 id 非法: " + objectId));
        }
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                    rule.getSeverity(), "节点不存在: " + nodeId));
        }
        JsonNode p = predicate(rule);
        if (p.path("requireActualDate").asBoolean(false) && node.getActualDate() == null) {
            return RuleEval.of(RuleOutcome.fail(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                    rule.getSeverity(), "节点 " + node.getNodeCode() + " 缺实际完成日期"));
        }
        String requireStatus = p.path("requireStatus").asText(null);
        if (StringUtils.hasText(requireStatus) && !requireStatus.equalsIgnoreCase(node.getStatus())) {
            return RuleEval.of(RuleOutcome.fail(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                    rule.getSeverity(), "节点 " + node.getNodeCode() + " 状态为 " + node.getStatus()
                    + "，期望 " + requireStatus));
        }
        return RuleEval.of(RuleOutcome.pass(rule.getId(), GateRuleType.NODE, rule.getRuleKey(),
                rule.getSeverity(), "节点事实齐备"));
    }

    private RuleEval evalApproval(GateRule rule, String objectType, String objectId) {
        if (!OBJ_NODE.equals(objectType)) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.APPROVAL, rule.getRuleKey(),
                    rule.getSeverity(), "APPROVAL 规则仅适用于 object_type=node，当前 " + objectType));
        }
        Long nodeId = parseId(objectId);
        if (nodeId == null) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.APPROVAL, rule.getRuleKey(),
                    rule.getSeverity(), "节点 id 非法: " + objectId));
        }
        int minLevels = Math.max(1, predicate(rule).path("minLevels").asInt(2));
        NodeApprovalService.ApprovalState s = nodeApprovalService.state(nodeId);

        Set<String> approvers = new LinkedHashSet<>();
        if (ProjectNodeApproval.APPROVED.equals(s.rdLeadDecision()) && StringUtils.hasText(s.rdLeadApprover())) {
            approvers.add(s.rdLeadApprover());
        }
        if (ProjectNodeApproval.APPROVED.equals(s.gmDecision()) && StringUtils.hasText(s.gmApprover())) {
            approvers.add(s.gmApprover());
        }
        if (approvers.size() >= minLevels) {
            return RuleEval.of(RuleOutcome.pass(rule.getId(), GateRuleType.APPROVAL, rule.getRuleKey(),
                    rule.getSeverity(), "已取得 " + approvers.size() + " 级有效审批: " + approvers));
        }
        String why;
        if (ProjectNodeApproval.REJECTED.equals(s.rdLeadDecision())
                || ProjectNodeApproval.REJECTED.equals(s.gmDecision())) {
            why = "审批被驳回";
        } else if (ProjectNodeApproval.APPROVED.equals(s.rdLeadDecision())
                && ProjectNodeApproval.APPROVED.equals(s.gmDecision())) {
            why = "两级审批人为同一人(" + approvers + ")，不构成 " + minLevels + " 级审批";
        } else {
            why = "审批未完成（研发主管=" + nz(s.rdLeadDecision()) + ", 总经理=" + nz(s.gmDecision()) + "）";
        }
        return RuleEval.of(RuleOutcome.fail(rule.getId(), GateRuleType.APPROVAL, rule.getRuleKey(),
                rule.getSeverity(), "关键节点双级审批不满足: " + why));
    }

    private RuleEval evalEvidence(GateRule rule, String objectType, String objectId) {
        JsonNode p = predicate(rule);
        int minCount = Math.max(1, p.path("minCount").asInt(1));
        String typeFilter = p.path("evidenceType").asText(null);
        List<PlmEvidence> all = evidenceService.listBySubject(objectType, objectId);
        List<PlmEvidence> hit = all.stream()
                .filter(e -> !StringUtils.hasText(typeFilter) || typeFilter.equals(e.getEvidenceType()))
                .toList();
        List<String> ids = hit.stream().map(PlmEvidence::getEvidenceId).toList();
        if (hit.size() >= minCount) {
            return new RuleEval(RuleOutcome.pass(rule.getId(), GateRuleType.EVIDENCE, rule.getRuleKey(),
                    rule.getSeverity(), "证据 " + hit.size() + " 条，满足 >= " + minCount), ids);
        }
        return new RuleEval(RuleOutcome.fail(rule.getId(), GateRuleType.EVIDENCE, rule.getRuleKey(),
                rule.getSeverity(), "证据不足: 实际 " + hit.size() + " 条，要求 >= " + minCount
                + (StringUtils.hasText(typeFilter) ? "（类型 " + typeFilter + "）" : "")), ids);
    }

    private RuleEval evalMetric(GateRule rule, String objectType, String objectId) {
        JsonNode p = predicate(rule);
        String metricCode = p.path("metricCode").asText(null);
        if (!StringUtils.hasText(metricCode)) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.METRIC, rule.getRuleKey(),
                    rule.getSeverity(), "METRIC 规则未声明 metricCode"));
        }
        List<BigDecimal> vs = jdbcTemplate.query(
                "SELECT value_calc FROM plm_metric_value WHERE metric_code=? AND object_type=? AND object_id=? "
                        + "ORDER BY grain_time DESC, id DESC LIMIT 1",
                (rs, i) -> rs.getBigDecimal(1), metricCode, objectType, objectId);
        if (vs.isEmpty() || vs.get(0) == null) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.METRIC, rule.getRuleKey(),
                    rule.getSeverity(), "指标 " + metricCode + " 无值（输入缺失），无法判定"));
        }
        BigDecimal v = vs.get(0);
        if (p.hasNonNull("min") && v.compareTo(p.get("min").decimalValue()) < 0) {
            return RuleEval.of(RuleOutcome.fail(rule.getId(), GateRuleType.METRIC, rule.getRuleKey(),
                    rule.getSeverity(), "指标 " + metricCode + "=" + v + " 低于下限 " + p.get("min")));
        }
        if (p.hasNonNull("max") && v.compareTo(p.get("max").decimalValue()) > 0) {
            return RuleEval.of(RuleOutcome.fail(rule.getId(), GateRuleType.METRIC, rule.getRuleKey(),
                    rule.getSeverity(), "指标 " + metricCode + "=" + v + " 高于上限 " + p.get("max")));
        }
        return RuleEval.of(RuleOutcome.pass(rule.getId(), GateRuleType.METRIC, rule.getRuleKey(),
                rule.getSeverity(), "指标 " + metricCode + "=" + v + " 满足阈值"));
    }

    /**
     * 契约齐套判定：对象必须登记一条 kit 声明证据
     * （{@code evidence_type=record}，{@code metadata} 形如
     * {@code {"kit":"TECH_TRANSFER_KIT","items":["bom","drawing",...]}}），
     * 且声明条目覆盖 predicate.required 的全部条目。
     */
    private RuleEval evalContract(GateRule rule, String objectType, String objectId) {
        JsonNode p = predicate(rule);
        List<String> required = new ArrayList<>();
        for (JsonNode n : p.path("required")) {
            required.add(n.asText());
        }
        if (required.isEmpty()) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.CONTRACT, rule.getRuleKey(),
                    rule.getSeverity(), "CONTRACT 规则未声明 required 条目"));
        }
        Set<String> provided = new LinkedHashSet<>();
        boolean declared = false;
        List<String> ids = new ArrayList<>();
        for (PlmEvidence e : evidenceService.listBySubject(objectType, objectId)) {
            JsonNode meta = readJson(e.getMetadata());
            if (meta == null) continue;
            if (!rule.getRuleKey().equals(meta.path("kit").asText(null))) continue;
            declared = true;
            ids.add(e.getEvidenceId());
            for (JsonNode n : meta.path("items")) {
                provided.add(n.asText());
            }
        }
        if (!declared) {
            return RuleEval.of(RuleOutcome.unknown(rule.getId(), GateRuleType.CONTRACT, rule.getRuleKey(),
                    rule.getSeverity(), "未找到 kit 声明(" + rule.getRuleKey() + ")，齐套情况不可判定"));
        }
        List<String> missing = required.stream().filter(k -> !provided.contains(k)).toList();
        if (missing.isEmpty()) {
            return new RuleEval(RuleOutcome.pass(rule.getId(), GateRuleType.CONTRACT, rule.getRuleKey(),
                    rule.getSeverity(), "齐套齐备: " + required), ids);
        }
        return new RuleEval(RuleOutcome.fail(rule.getId(), GateRuleType.CONTRACT, rule.getRuleKey(),
                rule.getSeverity(), "齐套缺口 " + missing.size() + " 项: " + missing), ids);
    }

    /** 四态聚合：BLOCK 的 FAIL/UNKNOWN 优先，WARN 不阻断。 */
    static GateStatus aggregate(List<RuleOutcome> outcomes) {
        if (outcomes.stream().anyMatch(o -> o.isBlock() && o.status() == GateStatus.FAIL)) {
            return GateStatus.FAIL;
        }
        if (outcomes.stream().anyMatch(o -> o.isBlock() && o.status() == GateStatus.UNKNOWN)) {
            return GateStatus.UNKNOWN;
        }
        return GateStatus.PASS;
    }

    private static String buildReason(GateStatus status, List<RuleOutcome> nonPass) {
        if (status == GateStatus.PASS) {
            return nonPass.isEmpty() ? "全部规则通过" : "放行；告警 " + nonPass.size() + " 项（WARN 级，不阻断）";
        }
        if (nonPass.isEmpty()) return status.name();
        return status + ": " + nonPass.get(0).reason()
                + (nonPass.size() > 1 ? " 等 " + nonPass.size() + " 项" : "");
    }

    // ---------------------------------------------------------------- 基础设施

    private Gate findGate(String gateCode) {
        if (!StringUtils.hasText(gateCode)) return null;
        LambdaQueryWrapper<Gate> w = new LambdaQueryWrapper<>();
        w.eq(Gate::getGateCode, gateCode).last("LIMIT 1");
        List<Gate> list = gateMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<GateRule> enabledRules(Long gateId) {
        LambdaQueryWrapper<GateRule> w = new LambdaQueryWrapper<>();
        w.eq(GateRule::getGateId, gateId)
                .eq(GateRule::getEnabled, true)
                .orderByAsc(GateRule::getId);
        return gateRuleMapper.selectList(w);
    }

    private GateResult persist(GateStatus status, String gateCode, String gateName, String gateVersion,
                               String objectType, String objectId, List<RuleOutcome> outcomes,
                               List<RuleOutcome> nonPass, List<String> evidenceIds, String reason,
                               String version) {
        LocalDateTime now = LocalDateTime.now();
        String by = currentUser();
        GateInstance inst = new GateInstance();
        inst.setGateCode(gateCode);
        inst.setObjectType(objectType);
        inst.setObjectId(objectId);
        inst.setStatus(status.name());
        inst.setFailedRules(toJson(nonPass));
        inst.setEvidenceRefs(toJson(evidenceIds));
        inst.setReason(truncate(reason, 500));
        inst.setEvaluatedBy(by);
        inst.setEvaluatedAt(now);
        try {
            gateInstanceMapper.insert(inst);
        } catch (Exception e) {
            log.warn("[gate] 判定留痕写入失败 {} {}/{}: {}", gateCode, objectType, objectId, e.getMessage());
        }
        // outcomes 必须原样回传：调用方要能逐条复核「哪些规则被判过、各是什么结论」。
        // 只回传未通过项会让放行判定证据缺一半 —— 通过的规则在响应里彻底消失。
        return new GateResult(gateCode, gateName, objectType, objectId, status,
                outcomes, nonPass, reason, version, by, now);
    }

    private JsonNode predicate(GateRule rule) {
        JsonNode n = readJson(rule.getPredicate());
        return n == null ? objectMapper.createObjectNode() : n;
    }

    private JsonNode readJson(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nz(String s) { return s == null ? "-" : s; }

    private static Long parseId(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String currentUser() {
        try {
            String name = SecurityUtils.getCurrentRealName();
            if (StringUtils.hasText(name)) return name;
            String u = SecurityUtils.getCurrentUsername();
            return StringUtils.hasText(u) ? u : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
