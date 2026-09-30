package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.deh.entity.AgentResult;
import com.hjgd.plm.deh.entity.PlmDecision;
import com.hjgd.plm.deh.mapper.AgentResultMapper;
import com.hjgd.plm.deh.mapper.PlmDecisionMapper;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.service.EvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 穿透链端到端追溯（F09 / F15 / 架构 V0.2 §3.4）。
 *
 * <p>把架构 §3.4 的六段样例做成可执行查询，而不是只写在文档里：
 * <pre>
 *   管理结果 → Decision → Action → Result → Verify → 原始记录
 * </pre>
 *
 * <p>每段都返回「该段对象 + 该段的证据 + 证据的 {@code source_record_id}」。
 * <b>断点判定</b>：某段没有证据，或证据的 {@code source_record_id} 为空，
 * 该段即断点 —— 因为此时无法从管理数字穿到它赖以成立的原始记录。
 * {@code brokenCount == 0} 即 AC-G3「断点=0」（本例六段全通）。
 *
 * <p>链路的对象串联字段：{@code source_type/source_id → decision_id → action_id
 * → result_id → verified_by}，与证据链（{@code subject_type/subject_id +
 * source_record_id}）互为表里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainTraceService {

    private final EvidenceService evidenceService;
    private final PlmDecisionMapper decisionMapper;
    private final AgentActionMapper actionMapper;
    private final AgentResultMapper resultMapper;

    /** 从指标对象出发追溯（穿透样例第 1 段即指标）。 */
    public Map<String, Object> traceFromMetric(String metricCode) {
        return trace("metric", metricCode);
    }

    /**
     * 通用穿透追溯。
     *
     * @param subjectType 起点对象类型（metric / decision / action / result / object / node）
     * @param subjectId   起点对象 ID
     */
    public Map<String, Object> trace(String subjectType, String subjectId) {
        List<Map<String, Object>> segments = new ArrayList<>();
        List<Map<String, Object>> broken = new ArrayList<>();

        // 第 1 段：管理结果（起点）
        addSegment(segments, broken, 1, "管理结果（触发）", subjectType, subjectId, "—（起点）");

        // 第 2 段：Decision —— 由起点对象触发（source_type/source_id 指回起点）
        List<PlmDecision> decisions = decisionsBySource(subjectType, subjectId);
        if (decisions.isEmpty()) {
            broken.add(brokenNode(2, "Decision", "无决策引用该起点（object_type=" + subjectType
                    + ", object_id=" + subjectId + "）"));
        }
        String primaryDecisionId = decisions.isEmpty() ? null : decisions.get(0).getDecisionId();
        if (primaryDecisionId != null) {
            addSegment(segments, broken, 2, "Decision", "decision", primaryDecisionId,
                    "object_type/object_id 或 source_type/source_id");
        }

        // 第 3 段：Action —— 由决策派生（decision_id 指回决策）
        List<AgentAction> actions = primaryDecisionId == null
                ? List.of() : actionsByDecision(primaryDecisionId);
        if (primaryDecisionId != null && actions.isEmpty()) {
            broken.add(brokenNode(3, "Action", "决策[" + primaryDecisionId + "]尚未派生动作"));
        }
        AgentAction primaryAction = actions.isEmpty() ? null : actions.get(0);
        if (primaryAction != null) {
            addSegment(segments, broken, 3, "Action", "action", primaryAction.getActionId(), "decision_id");
        }

        // 第 4 段：Result —— 由动作提交（action_id 指回动作）
        AgentResult result = primaryAction == null ? null : latestResultOfAction(primaryAction.getActionId());
        if (primaryAction != null && result == null) {
            broken.add(brokenNode(4, "Result", "动作[" + primaryAction.getActionId() + "]尚未提交结果"));
        }
        if (result != null) {
            addSegment(segments, broken, 4, "Result", "result", result.getResultId(), "action_id");
        }

        // 第 5 段：Verify —— 独立验证（仅 result 被接受时成立）
        if (result != null) {
            addSegment(segments, broken, 5, "Verify", "result", result.getResultId(), "result_id");
            if (!"verified".equals(result.getStatus())) {
                broken.add(brokenNode(5, "Verify", "结果[" + result.getResultId() + "]状态为 "
                        + result.getStatus() + "，尚未独立验证（done ≠ verified）"));
            }
        }

        // 第 6 段：原始记录 —— 各段证据 source_record_id 指向的最原始记录
        List<Map<String, Object>> rawRecords = collectRawRecords(segments);
        if (rawRecords.isEmpty()) {
            broken.add(brokenNode(6, "原始记录", "全链证据均未指向原始记录（source_record_id 为空）"));
        } else {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("segment", 6);
            seg.put("name", "原始记录");
            seg.put("objectType", "raw_record");
            seg.put("objectId", rawRecords.get(0).get("sourceRecordId"));
            seg.put("linkField", "subject_type/subject_id → source_record_id");
            seg.put("evidence", List.of());
            seg.put("rawRecords", rawRecords);
            seg.put("traversable", true);
            segments.add(seg);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", Map.of("type", subjectType, "id", subjectId));
        out.put("segments", segments);
        out.put("segmentCount", segments.size());
        out.put("brokenLinks", broken);
        out.put("brokenCount", broken.size());
        out.put("complete", broken.isEmpty() && segments.size() >= 6);
        out.put("branches", Map.of(
                "decisionCount", decisions.size(),
                "actionCount", actions.size()));
        return out;
    }

    // ------------------------------------------------------------------ 内部

    /** 追加一段：取该段对象的证据，逐条检查 source_record_id 是否非空。 */
    private void addSegment(List<Map<String, Object>> segments, List<Map<String, Object>> broken,
                            int index, String name, String objectType, String objectId, String linkField) {
        List<PlmEvidence> evidences = evidenceService.listBySubject(objectType, objectId);
        List<Map<String, Object>> links = new ArrayList<>();
        boolean traversable = false;
        for (PlmEvidence e : evidences) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("evidenceId", e.getEvidenceId());
            link.put("evidenceType", e.getEvidenceType());
            link.put("sourceRecordId", e.getSourceRecordId());
            links.add(link);
            if (StringUtils.hasText(e.getSourceRecordId())) traversable = true;
        }

        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put("segment", index);
        seg.put("name", name);
        seg.put("objectType", objectType);
        seg.put("objectId", objectId);
        seg.put("linkField", linkField);
        seg.put("evidence", links);
        seg.put("traversable", traversable);
        segments.add(seg);

        if (evidences.isEmpty()) {
            broken.add(brokenNode(index, name, "该段无证据（subject_type=" + objectType
                    + ", subject_id=" + objectId + "）"));
        } else if (!traversable) {
            broken.add(brokenNode(index, name, "该段证据均未指向原始记录（source_record_id 为空）"));
        }
    }

    /** 汇总全链证据指向的原始记录（去重，保持出现顺序）。 */
    private List<Map<String, Object>> collectRawRecords(List<Map<String, Object>> segments) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map<String, Object> seg : segments) {
            Object ev = seg.get("evidence");
            if (!(ev instanceof List<?> list)) continue;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object src = m.get("sourceRecordId");
                if (src != null && StringUtils.hasText(src.toString())) {
                    seen.putIfAbsent(src.toString(), String.valueOf(seg.get("name")));
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        seen.forEach((src, fromSegment) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sourceRecordId", src);
            r.put("fromSegment", fromSegment);
            out.add(r);
        });
        return out;
    }

    /**
     * 第 1→2 段：找出由起点触发的决策。
     *
     * <p>按 DEH §3，{@code source_type} 只取 meeting|event|issue|agent|human|system
     * ——业务对象（如指标）不在此列，它挂在 {@code object_ref} 上。因此这里两处任一
     * 指向起点即算命中：{@code source_type/source_id}（触发来源）或
     * {@code object_type/object_id}（被指向的业务对象）。只查前者会让「指标 → 决策」
     * 这一段永远接不上。
     */
    private List<PlmDecision> decisionsBySource(String sourceType, String sourceId) {
        LambdaQueryWrapper<PlmDecision> w = new LambdaQueryWrapper<>();
        w.and(inner -> inner
                        .eq(PlmDecision::getSourceType, sourceType)
                        .eq(PlmDecision::getSourceId, sourceId))
                .or(inner -> inner
                        .eq(PlmDecision::getObjectType, sourceType)
                        .eq(PlmDecision::getObjectId, sourceId))
                .orderByAsc(PlmDecision::getCreatedAt);
        return decisionMapper.selectList(w);
    }

    private List<AgentAction> actionsByDecision(String decisionId) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        w.eq(AgentAction::getDecisionId, decisionId).orderByAsc(AgentAction::getId);
        return actionMapper.selectList(w);
    }

    private AgentResult latestResultOfAction(String actionId) {
        LambdaQueryWrapper<AgentResult> w = new LambdaQueryWrapper<>();
        w.eq(AgentResult::getActionId, actionId).orderByDesc(AgentResult::getSubmittedAt);
        List<AgentResult> list = resultMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    private static Map<String, Object> brokenNode(int segment, String name, String reason) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("segment", segment);
        b.put("name", name);
        b.put("reason", reason);
        return b;
    }
}
