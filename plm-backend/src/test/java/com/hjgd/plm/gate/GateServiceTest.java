package com.hjgd.plm.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.service.EvidenceService;
import com.hjgd.plm.gate.domain.GateResult;
import com.hjgd.plm.gate.domain.GateStatus;
import com.hjgd.plm.gate.domain.RuleOutcome;
import com.hjgd.plm.gate.entity.Gate;
import com.hjgd.plm.gate.entity.GateInstance;
import com.hjgd.plm.gate.entity.GateRule;
import com.hjgd.plm.gate.mapper.GateInstanceMapper;
import com.hjgd.plm.gate.mapper.GateMapper;
import com.hjgd.plm.gate.mapper.GateRuleMapper;
import com.hjgd.plm.gate.service.GateService;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.NodeApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F08 统一 Gate 引擎：四态语义、单一判定入口、UNKNOWN ≠ PASS。
 */
@DisplayName("F08 统一 Gate 引擎")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GateServiceTest {

    private static final String NODE_GATE = GateService.GATE_NODE_KEY_DONE;

    @Mock private GateMapper gateMapper;
    @Mock private GateRuleMapper gateRuleMapper;
    @Mock private GateInstanceMapper gateInstanceMapper;
    @Mock private ProjectNodeMapper nodeMapper;
    @Mock private NodeApprovalService nodeApprovalService;
    @Mock private EvidenceService evidenceService;
    @Mock private JdbcTemplate jdbcTemplate;
    private GateService service;

    @BeforeEach
    void setUp() {
        // 真实的 JSON 解析/序列化，避免用 mock 掩盖 predicate 解析问题
        service = new GateService(gateMapper, gateRuleMapper, gateInstanceMapper, nodeMapper,
                nodeApprovalService, evidenceService, jdbcTemplate, new ObjectMapper());
    }

    // ---------------------------------------------------------------- 脚手架

    private Gate gate(String code, boolean enabled) {
        Gate g = new Gate();
        g.setId(10L);
        g.setGateCode(code);
        g.setName("测试门");
        g.setSeq(1);
        g.setVersion("V1");
        g.setEnabled(enabled);
        return g;
    }

    private GateRule rule(Long id, String type, String key, String predicate, String severity) {
        GateRule r = new GateRule();
        r.setId(id);
        r.setGateId(10L);
        r.setRuleType(type);
        r.setRuleKey(key);
        r.setPredicate(predicate);
        r.setSeverity(severity);
        r.setEnabled(true);
        return r;
    }

    private void stubGate(Gate g, GateRule... rules) {
        when(gateMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(g)));
        when(gateRuleMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(rules)));
    }

    private ProjectNode node(Long id, String code, LocalDate actualDate) {
        ProjectNode n = new ProjectNode();
        n.setId(id);
        n.setProjectNo("HJ001");
        n.setNodeCode(code);
        n.setNodeName(code);
        n.setIsKey(1);
        n.setStatus("DONE");
        n.setActualDate(actualDate);
        return n;
    }

    private void stubApproval(String l1Decision, String l1By, String l2Decision, String l2By) {
        when(nodeApprovalService.state(anyLong())).thenReturn(
                new NodeApprovalService.ApprovalState(l1Decision, l1By, l2Decision, l2By));
    }

    private PlmEvidence evidence(String id, String type, String metadata) {
        PlmEvidence e = new PlmEvidence();
        e.setEvidenceId(id);
        e.setEvidenceType(type);
        e.setMetadata(metadata);
        e.setVersion("V1");
        e.setSourceRecordId("src-" + id);
        return e;
    }

    // ---------------------------------------------------------------- 四态语义

    @Test
    @DisplayName("未注册的 Gate → UNKNOWN(不得视为 PASS)")
    void unregisteredGateIsUnknown() {
        when(gateMapper.selectList(any())).thenReturn(new ArrayList<>());

        GateResult r = service.evaluate("GATE_NOT_EXIST", "node", "1");

        assertEquals(GateStatus.UNKNOWN, r.status());
        assertFalse(r.passed());
        assertTrue(r.reason().contains("未注册"));
        // 判定留痕
        verify(gateInstanceMapper).insert(any(GateInstance.class));
    }

    @Test
    @DisplayName("已注册但未配置规则 → UNKNOWN(未配置不得放行)")
    void gateWithoutRulesIsUnknown() {
        stubGate(gate(NODE_GATE, true));

        GateResult r = service.evaluate(NODE_GATE, "node", "1");

        assertEquals(GateStatus.UNKNOWN, r.status());
        assertFalse(r.passed());
        assertTrue(r.reason().contains("未配置"));
    }

    @Test
    @DisplayName("已停用的 Gate → UNKNOWN")
    void disabledGateIsUnknown() {
        stubGate(gate(NODE_GATE, false));

        assertEquals(GateStatus.UNKNOWN, service.evaluate(NODE_GATE, "node", "1").status());
    }

    @Test
    @DisplayName("G0-G8 尚无规则 → 全部 UNKNOWN, 无一被当成已放行")
    void stageGatesWithoutRulesAreAllUnknown() {
        stubGate(gate("GATE_G3", true));
        assertEquals(GateStatus.UNKNOWN, service.evaluate("GATE_G3", "stage", "HJ001").status());
    }

    // ---------------------------------------------------------------- 规则

    @Test
    @DisplayName("EVIDENCE: 证据不足 → FAIL, 留痕记录启用证据引用")
    void evidenceRuleFailsWhenInsufficient() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(evidenceService.listBySubject("node", "1")).thenReturn(new ArrayList<>());

        GateResult r = service.evaluate(NODE_GATE, "node", "1");

        assertEquals(GateStatus.FAIL, r.status());
        assertTrue(r.failedRules().get(0).reason().contains("证据不足"));
    }

    @Test
    @DisplayName("EVIDENCE: 证据齐备 → PASS, 判定实例记录证据引用")
    void evidenceRulePassesAndRecordsRefs() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(evidenceService.listBySubject("node", "1"))
                .thenReturn(List.of(evidence("EV-1", "file", null)));

        GateResult r = service.evaluate(NODE_GATE, "node", "1");

        assertEquals(GateStatus.PASS, r.status());
        var cap = org.mockito.ArgumentCaptor.forClass(GateInstance.class);
        verify(gateInstanceMapper).insert(cap.capture());
        assertTrue(cap.getValue().getEvidenceRefs().contains("EV-1"));
    }

    @Test
    @DisplayName("NODE: 缺实际完成日期 → FAIL; 补齐 → PASS")
    void nodeRuleRequiresActualDate() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "NODE", "ACTUAL_DATE", "{\"requireActualDate\":true}", "BLOCK"));
        when(nodeMapper.selectById(7L)).thenReturn(node(7L, "MOLD_REVIEW", null));

        assertEquals(GateStatus.FAIL, service.evaluate(NODE_GATE, "node", "7").status());

        when(nodeMapper.selectById(7L)).thenReturn(node(7L, "MOLD_REVIEW", LocalDate.now()));
        assertEquals(GateStatus.PASS, service.evaluate(NODE_GATE, "node", "7").status());
    }

    @Test
    @DisplayName("APPROVAL: 两个不同责任人各批一级 → PASS")
    void approvalRulePassesWithTwoDistinctApprovers() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "APPROVAL", "DUAL_LEVEL", "{\"minLevels\":2}", "BLOCK"));
        stubApproval(ProjectNodeApproval.APPROVED, "张三",
                ProjectNodeApproval.APPROVED, "李四");

        assertEquals(GateStatus.PASS, service.evaluate(NODE_GATE, "node", "7").status());
    }

    @Test
    @DisplayName("APPROVAL: 同一人批两级 → FAIL(不构成双级)")
    void approvalRuleFailsWhenSameApprover() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "APPROVAL", "DUAL_LEVEL", "{\"minLevels\":2}", "BLOCK"));
        stubApproval(ProjectNodeApproval.APPROVED, "张三",
                ProjectNodeApproval.APPROVED, "张三");

        GateResult r = service.evaluate(NODE_GATE, "node", "7");
        assertEquals(GateStatus.FAIL, r.status());
        assertTrue(r.failedRules().get(0).reason().contains("同一人"));
    }

    @Test
    @DisplayName("APPROVAL: 被驳回 → FAIL")
    void approvalRuleFailsWhenRejected() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "APPROVAL", "DUAL_LEVEL", "{\"minLevels\":2}", "BLOCK"));
        stubApproval(ProjectNodeApproval.REJECTED, "张三",
                ProjectNodeApproval.APPROVED, "李四");

        assertEquals(GateStatus.FAIL, service.evaluate(NODE_GATE, "node", "7").status());
    }

    @Test
    @DisplayName("METRIC: 指标无值属「输入缺失」→ UNKNOWN, 不是 FAIL 也不是 PASS")
    void metricRuleWithoutValueIsUnknown() {
        stubGate(gate("GATE_G6", true),
                rule(1L, "METRIC", "ADOPT", "{\"metricCode\":\"M_AGENT_ADOPT\"," +
                        "\"min\":0.8}", "BLOCK"));
        doReturn(new ArrayList<BigDecimal>()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any(), any());

        GateResult r = service.evaluate("GATE_G6", "project", "HJ001");

        assertEquals(GateStatus.UNKNOWN, r.status());
        assertFalse(r.passed());
    }

    @Test
    @DisplayName("METRIC: 有值且越界 → FAIL; 达标 → PASS")
    void metricRuleThresholds() {
        stubGate(gate("GATE_G6", true),
                rule(1L, "METRIC", "ADOPT", "{\"metricCode\":\"M_AGENT_ADOPT\",\"min\":0.8}", "BLOCK"));

        doReturn(List.of(new BigDecimal("0.50"))).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any(), any());
        assertEquals(GateStatus.FAIL, service.evaluate("GATE_G6", "project", "HJ001").status());

        doReturn(List.of(new BigDecimal("0.95"))).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any(), any());
        assertEquals(GateStatus.PASS, service.evaluate("GATE_G6", "project", "HJ001").status());
    }

    @Test
    @DisplayName("CONTRACT: 无 kit 声明 → UNKNOWN(齐套情况不可判定)")
    void contractRuleWithoutDeclarationIsUnknown() {
        stubGate(gate(GateService.GATE_TECH_TRANSFER, true),
                rule(1L, "CONTRACT", "TECH_TRANSFER_KIT",
                        "{\"required\":[\"bom\",\"drawing\"]}", "BLOCK"));
        when(evidenceService.listBySubject("project", "HJ001")).thenReturn(new ArrayList<>());

        assertEquals(GateStatus.UNKNOWN,
                service.evaluate(GateService.GATE_TECH_TRANSFER, "project", "HJ001").status());
    }

    @Test
    @DisplayName("CONTRACT: 齐套缺口 → FAIL, 缺项一并列出")
    void contractRuleReportsMissingItems() {
        stubGate(gate(GateService.GATE_TECH_TRANSFER, true),
                rule(1L, "CONTRACT", "TECH_TRANSFER_KIT",
                        "{\"required\":[\"bom\",\"drawing\",\"sop\"]}", "BLOCK"));
        when(evidenceService.listBySubject("project", "HJ001")).thenReturn(List.of(
                evidence("K-1", "record", "{\"kit\":\"TECH_TRANSFER_KIT\",\"items\":[\"bom\"]}")));

        GateResult r = service.evaluate(GateService.GATE_TECH_TRANSFER, "project", "HJ001");

        assertEquals(GateStatus.FAIL, r.status());
        String reason = r.failedRules().get(0).reason();
        assertTrue(reason.contains("drawing"));
        assertTrue(reason.contains("sop"));
    }

    @Test
    @DisplayName("CONTRACT: 六项齐备 → PASS")
    void contractRulePassesWhenKitComplete() {
        stubGate(gate(GateService.GATE_TECH_TRANSFER, true),
                rule(1L, "CONTRACT", "TECH_TRANSFER_KIT",
                        "{\"required\":[\"bom\",\"drawing\",\"sop\"]}", "BLOCK"));
        when(evidenceService.listBySubject("project", "HJ001")).thenReturn(List.of(
                evidence("K-1", "record",
                        "{\"kit\":\"TECH_TRANSFER_KIT\",\"items\":[\"bom\",\"drawing\",\"sop\"]}")));

        assertEquals(GateStatus.PASS,
                service.evaluate(GateService.GATE_TECH_TRANSFER, "project", "HJ001").status());
    }

    @Test
    @DisplayName("WARN 规则未通过不阻断放行, 但记录在案")
    void warnRuleDoesNotBlock() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "NODE", "SEQUENCE", "{\"requireActualDate\":true}", "WARN"));
        when(nodeMapper.selectById(7L)).thenReturn(node(7L, "MOLD", null));

        GateResult r = service.evaluate(NODE_GATE, "node", "7");

        assertEquals(GateStatus.PASS, r.status());
        assertTrue(r.passed());
        assertEquals(1, r.failedRules().size(), "告警项应保留在 failedRules 供复核");
        assertTrue(r.reason().contains("告警"));
    }

    @Test
    @DisplayName("BLOCK 优先: 同时存在 BLOCK 与 WARN 失败时判 FAIL")
    void blockTakesPrecedenceOverWarn() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "NODE", "SEQ_WARN", "{\"requireActualDate\":true}", "WARN"),
                rule(2L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(nodeMapper.selectById(7L)).thenReturn(node(7L, "MOLD", null));
        when(evidenceService.listBySubject("node", "7")).thenReturn(new ArrayList<>());

        assertEquals(GateStatus.FAIL, service.evaluate(NODE_GATE, "node", "7").status());
    }

    // ---------------------------------------------------------------- 断言与留痕

    @Test
    @DisplayName("assertPassed: 未放行时抛 GATE_DENIED, 并说明状态与原因")
    void assertPassedThrowsGateDenied() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(evidenceService.listBySubject("node", "7")).thenReturn(new ArrayList<>());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertPassed(NODE_GATE, "node", "7"));
        assertTrue(ex.getMessage().contains("GATE_DENIED"));
        assertTrue(ex.getMessage().contains("FAIL"));
    }

    @Test
    @DisplayName("assertPassed: 放行时不抛异常")
    void assertPassedReturnsWhenGatePasses() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(evidenceService.listBySubject("node", "7"))
                .thenReturn(List.of(evidence("EV-1", "file", null)));

        assertDoesNotThrow(() -> service.assertPassed(NODE_GATE, "node", "7"));
    }

    @Test
    @DisplayName("evaluate 与 peek 逐条结论一致: 通过的规则也不得从响应里消失")
    void evaluateReturnsSameOutcomesAsPeek() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"),
                rule(2L, "NODE", "ACTUAL_DATE", "{\"requireActualDate\":true}", "BLOCK"));
        when(evidenceService.listBySubject("node", "7"))
                .thenReturn(List.of(evidence("EV-1", "file", null)));
        when(nodeMapper.selectById(7L)).thenReturn(node(7L, "MOLD", LocalDate.now()));

        GateResult evaluated = service.evaluate(NODE_GATE, "node", "7");
        GateResult peeked = service.peek(NODE_GATE, "node", "7");

        assertEquals(GateStatus.PASS, evaluated.status());
        assertTrue(evaluated.failedRules().isEmpty());
        assertEquals(2, evaluated.outcomes().size(),
                "两条规则都通过, 响应仍须逐条回传, 否则放行判定证据缺一半");
        assertEquals(peeked.outcomes().stream().map(RuleOutcome::ruleKey).toList(),
                evaluated.outcomes().stream().map(RuleOutcome::ruleKey).toList(),
                "同一引擎: 两个入口的逐条结论必须一致");
    }

    @Test
    @DisplayName("peek: 只读预检不留痕, 结论与 evaluate 一致")
    void peekDoesNotPersist() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));
        when(evidenceService.listBySubject("node", "7")).thenReturn(new ArrayList<>());

        GateResult r = service.peek(NODE_GATE, "node", "7");

        assertEquals(GateStatus.FAIL, r.status());
        verify(gateInstanceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("catalog: 列出全部门定义并带规则数")
    void catalogListsGatesWithRuleCount() {
        stubGate(gate(NODE_GATE, true),
                rule(1L, "EVIDENCE", "NODE_EVIDENCE", "{\"minCount\":1}", "BLOCK"));

        var catalog = service.catalog();

        assertEquals(1, catalog.size());
        assertEquals(NODE_GATE, catalog.get(0).get("gateCode"));
        assertEquals(1, catalog.get(0).get("ruleCount"));
        assertEquals("V1", catalog.get(0).get("version"), "判定依据版本可追溯");
    }

    @Test
    @DisplayName("catalog: 未配置规则的门规则数为 0(G0-G8 当前状态)")
    void catalogShowsZeroRulesWhenUnconfigured() {
        stubGate(gate("GATE_G0", true));

        var catalog = service.catalog();

        assertEquals(1, catalog.size());
        assertEquals(0, catalog.get(0).get("ruleCount"));
        assertEquals("GATE_G0", catalog.get(0).get("gateCode"));
    }

    @Test
    @DisplayName("history: 判定实例即「已放行」的留痕证据")
    void historyReturnsInstances() {
        GateInstance inst = new GateInstance();
        inst.setGateCode(NODE_GATE);
        inst.setStatus("PASS");
        when(gateInstanceMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(inst)));

        var list = service.history(NODE_GATE, "node", "7");

        assertEquals(1, list.size());
        assertEquals("PASS", list.get(0).getStatus());
        assertEquals("PASS", service.latestInstance(NODE_GATE, "node", "7").getStatus());
    }

    @Test
    @DisplayName("未知规则类型 → 该规则 UNKNOWN, 不阻断但也不放行")
    void unknownRuleTypeYieldsUnknown() {
        stubGate(gate("GATE_G4", true),
                rule(1L, "MAGIC", "X", "{}", "BLOCK"));

        assertEquals(GateStatus.UNKNOWN, service.evaluate("GATE_G4", "project", "HJ001").status());
    }
}
