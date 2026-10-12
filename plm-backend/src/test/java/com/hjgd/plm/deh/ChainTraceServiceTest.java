package com.hjgd.plm.deh;

import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.deh.entity.AgentResult;
import com.hjgd.plm.deh.entity.PlmDecision;
import com.hjgd.plm.deh.mapper.AgentResultMapper;
import com.hjgd.plm.deh.mapper.PlmDecisionMapper;
import com.hjgd.plm.deh.service.ChainTraceService;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.service.EvidenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F09/F15 穿透链六段：管理结果 → Decision → Action → Result → Verify → 原始记录。
 *
 * <p>断言的是架构 V0.2 §3.4 样例的可执行性：每段有证据且 {@code source_record_id} 非空
 * 才不算断点；{@code brokenCount == 0} 即 AC-G3「断点=0」。
 */
@DisplayName("F09 穿透链六段追溯")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainTraceServiceTest {

    @Mock private EvidenceService evidenceService;
    @Mock private PlmDecisionMapper decisionMapper;
    @Mock private AgentActionMapper actionMapper;
    @Mock private AgentResultMapper resultMapper;
    @InjectMocks private ChainTraceService service;

    private PlmEvidence ev(String id, String subjectType, String subjectId, String type, String sourceRecordId) {
        PlmEvidence e = new PlmEvidence();
        e.setEvidenceId(id);
        e.setSubjectType(subjectType);
        e.setSubjectId(subjectId);
        e.setEvidenceType(type);
        e.setSourceRecordId(sourceRecordId);
        e.setSourceSystem("PLM2");
        e.setVersion("V1");
        e.setChecksum("sum-" + id);
        e.setConfidence(BigDecimal.ONE);
        return e;
    }

    /** 搭出架构 V0.2 §3.4 样例的六段链路。 */
    private void stubFullChain(boolean resultVerified) {
        when(evidenceService.listBySubject("metric", "METRIC-001")).thenReturn(List.of(
                ev("E-001", "metric", "METRIC-001", "metric", "plm_metric_object.METRIC-001"),
                ev("E-006", "metric", "METRIC-001", "log", "plm_project_change_log.CHG-2026-0097")));
        when(evidenceService.listBySubject("decision", "DEC-2026-009")).thenReturn(List.of(
                ev("E-002", "decision", "DEC-2026-009", "record", "DEC-2026-009")));
        when(evidenceService.listBySubject("action", "ACT-2026-014")).thenReturn(List.of(
                ev("E-003", "action", "ACT-2026-014", "record", "ACT-2026-014")));
        when(evidenceService.listBySubject("result", "RES-2026-006")).thenReturn(List.of(
                ev("E-004", "result", "RES-2026-006", "log", "commit:a1b2c3d"),
                ev("E-005", "result", "RES-2026-006", "record", "VER-2026-003")));

        PlmDecision d = new PlmDecision();
        d.setDecisionId("DEC-2026-009");
        d.setStatus("confirmed");
        when(decisionMapper.selectList(any())).thenReturn(List.of(d));

        AgentAction a = new AgentAction();
        a.setId(1L);
        a.setActionId("ACT-2026-014");
        a.setStatus("done");
        when(actionMapper.selectList(any())).thenReturn(List.of(a));

        AgentResult r = new AgentResult();
        r.setResultId("RES-2026-006");
        r.setActionId("ACT-2026-014");
        r.setStatus(resultVerified ? "verified" : "submitted");
        r.setSubmittedBy("zhangsan");
        when(resultMapper.selectList(any())).thenReturn(List.of(r));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> segments(Map<String, Object> out) {
        return (List<Map<String, Object>>) out.get("segments");
    }

    @Test
    @DisplayName("六段全通：断点=0，可穿透到原始记录")
    void fullChainIsTraversable() {
        stubFullChain(true);

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        assertEquals(6, out.get("segmentCount"), "样例是 6 段链路");
        assertEquals(0, out.get("brokenCount"), "断点必须为 0（AC-G3）");
        assertEquals(Boolean.TRUE, out.get("complete"));
        assertEquals(List.of("管理结果（触发）", "Decision", "Action", "Result", "Verify", "原始记录"),
                segments(out).stream().map(s -> s.get("name")).toList());
    }

    @Test
    @DisplayName("每段都带对象类型 + 对象 ID，可逐段回溯")
    void everySegmentCarriesObjectIdentity() {
        stubFullChain(true);

        List<Map<String, Object>> segs = segments(service.traceFromMetric("METRIC-001"));

        assertEquals("metric", segs.get(0).get("objectType"));
        assertEquals("METRIC-001", segs.get(0).get("objectId"));
        assertEquals("decision", segs.get(1).get("objectType"));
        assertEquals("DEC-2026-009", segs.get(1).get("objectId"));
        assertEquals("action", segs.get(2).get("objectType"));
        assertEquals("ACT-2026-014", segs.get(2).get("objectId"));
        assertEquals("result", segs.get(3).get("objectType"));
        assertEquals("RES-2026-006", segs.get(3).get("objectId"));
    }

    @Test
    @DisplayName("链末端落到原始记录：CF 口径的原始变更记录可查")
    void terminalSegmentHoldsRawRecord() {
        stubFullChain(true);

        Map<String, Object> out = service.traceFromMetric("METRIC-001");
        Map<String, Object> last = segments(out).get(5);

        assertEquals("原始记录", last.get("name"));
        assertEquals("plm_metric_object.METRIC-001", last.get("objectId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) last.get("rawRecords");
        assertTrue(raw.stream().anyMatch(r -> "plm_project_change_log.CHG-2026-0097".equals(r.get("sourceRecordId"))),
                "必须能回溯到 CF 口径的原始变更记录");
    }

    @Test
    @DisplayName("结果未验证 → Verify 段即断点（done ≠ verified 在链上看得见）")
    void unverifiedResultBreaksVerifySegment() {
        stubFullChain(false);

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        assertEquals(Boolean.FALSE, out.get("complete"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> broken = (List<Map<String, Object>>) out.get("brokenLinks");
        assertTrue(broken.stream().anyMatch(b -> "Verify".equals(b.get("name"))),
                "未验证的结果必须体现为 Verify 段断点");
    }

    @Test
    @DisplayName("某段无证据 → 该段断点")
    void missingEvidenceBreaksSegment() {
        stubFullChain(true);
        when(evidenceService.listBySubject("action", "ACT-2026-014")).thenReturn(List.of());

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        assertNotEquals(0, out.get("brokenCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> broken = (List<Map<String, Object>>) out.get("brokenLinks");
        assertTrue(broken.stream().anyMatch(b -> "Action".equals(b.get("name"))));
    }

    @Test
    @DisplayName("证据存在但 source_record_id 为空 → 仍然断链")
    void emptySourceRecordIdIsBroken() {
        stubFullChain(true);
        when(evidenceService.listBySubject("decision", "DEC-2026-009")).thenReturn(List.of(
                ev("E-002", "decision", "DEC-2026-009", "record", null)));

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        assertNotEquals(0, out.get("brokenCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> broken = (List<Map<String, Object>>) out.get("brokenLinks");
        assertTrue(broken.stream().anyMatch(b -> "Decision".equals(b.get("name"))
                        && String.valueOf(b.get("reason")).contains("source_record_id")),
                "有证据但没指向原始记录，同样到不了原始记录");
    }

    @Test
    @DisplayName("没有决策引用该指标 → Decision 段断点，链路到此为止")
    void noDecisionBreaksChain() {
        stubFullChain(true);
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(actionMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        assertEquals(Boolean.FALSE, out.get("complete"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> broken = (List<Map<String, Object>>) out.get("brokenLinks");
        assertTrue(broken.stream().anyMatch(b -> "Decision".equals(b.get("name"))));
    }

    @Test
    @DisplayName("终点指标证据缺失 → 管理结果段即不可穿透")
    void rootSegmentWithoutEvidence() {
        stubFullChain(true);
        when(evidenceService.listBySubject("metric", "METRIC-001")).thenReturn(List.of());

        Map<String, Object> out = service.traceFromMetric("METRIC-001");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> broken = (List<Map<String, Object>>) out.get("brokenLinks");
        assertTrue(broken.stream().anyMatch(b -> "管理结果（触发）".equals(b.get("name"))));
    }
}
