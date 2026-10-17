package com.hjgd.plm.project;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.service.EvidenceService;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.gate.service.GateService;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeEvidence;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeEvidenceMapper;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.GateAdvanceService;
import com.hjgd.plm.project.service.ProjectProgressService;
import com.hjgd.plm.rd.service.ChangeLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 研发项目进度跟踪: 22 节点初始化 + 完成硬标准(唯一判定链) + 自检健康分口径。
 */
@DisplayName("项目进度跟踪")
@ExtendWith(MockitoExtension.class)
class ProjectProgressServiceTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectNodeMapper nodeMapper;
    @Mock private ProjectNodeEvidenceMapper evidenceMapper;
    @Mock private FileService fileService;
    @Mock private DomainEventService domainEventService;
    @Mock private ChangeLogService changeLogService;
    @Mock private GateService gateService;
    @Mock private EvidenceService evidenceService;
    @Mock private GateAdvanceService gateAdvanceService;
    @InjectMocks private ProjectProgressService service;

    @Test
    @DisplayName("初始化: 每项目写入 22 个节点, 其中 7 个关键节点")
    void shouldInitTwentyTwoNodes() {
        service.initNodes(1L, "HJ001");

        ArgumentCaptor<ProjectNode> cap = ArgumentCaptor.forClass(ProjectNode.class);
        verify(nodeMapper, times(22)).insert(cap.capture());
        List<ProjectNode> nodes = cap.getAllValues();
        assertEquals("PLAN", nodes.get(0).getNodeCode());
        assertEquals("计划表", nodes.get(0).getNodeName());
        assertEquals("OTHER", nodes.get(21).getNodeCode());
        long keyCount = nodes.stream().filter(n -> n.getIsKey() == 1).count();
        assertEquals(7, keyCount, "关键节点应为 7 个");
        assertTrue(nodes.stream().allMatch(n -> "NOT_SET".equals(n.getStatus())));
    }

    @Test
    @DisplayName("完成硬标准: 缺实际日期 或 缺证据 均拒绝")
    void shouldEnforceCompletionStandard() {
        ProjectNode node = node("PROD_TRIAL", 1, "NOT_SET");
        when(nodeMapper.selectOne(any())).thenReturn(node);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        // 缺实际日期
        assertTrue(assertThrows(BusinessException.class, () -> service.updateNode(1L, "PROD_TRIAL", req))
                .getMessage().contains("实际完成日期"));

        // 有实际日期但无证据
        req.setActualDate(LocalDate.now());
        assertTrue(assertThrows(BusinessException.class, () -> service.updateNode(1L, "PROD_TRIAL", req))
                .getMessage().contains("证据"));
    }

    @Test
    @DisplayName("完成硬标准满足: 实际日期 + 证据>=1 → 通过")
    void shouldCompleteWhenEvidencePresent() {
        ProjectNode node = node("PROD_TRIAL", 1, "IN_PROGRESS");
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        req.setActualDate(LocalDate.now());
        ProjectNode out = service.updateNode(1L, "PROD_TRIAL", req);

        assertEquals("DONE", out.getStatus());
        assertNotNull(out.getActualDate());
        verify(nodeMapper, atLeastOnce()).updateById(node);
    }

    @Test
    @DisplayName("F08: 关键节点进入 DONE 必须过统一 Gate 放行判定")
    void shouldRequireGateForKeyNodeEnteringDone() {
        ProjectNode node = node("TEST_REPORT", 1, "IN_PROGRESS");
        node.setId(7L);
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        req.setActualDate(LocalDate.now());
        service.updateNode(1L, "TEST_REPORT", req);

        verify(gateService).assertPassed(eq(GateService.GATE_NODE_KEY_DONE), eq("node"), eq("7"));
    }

    @Test
    @DisplayName("F08: 非关键节点进入 DONE 不触发关键节点门")
    void shouldNotRequireGateForNonKeyNode() {
        ProjectNode node = node("PLAN", 0, "IN_PROGRESS");
        node.setId(3L);
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        req.setActualDate(LocalDate.now());
        service.updateNode(1L, "PLAN", req);

        verify(gateService, never()).assertPassed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("F08: 已放行的关键节点再次编辑不重复判门")
    void shouldNotReGateAlreadyDoneNode() {
        ProjectNode node = node("TEST_REPORT", 1, "DONE");
        node.setId(7L);
        node.setActualDate(LocalDate.now());
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNode req = new ProjectNode();
        req.setRemark("补充说明");
        service.updateNode(1L, "TEST_REPORT", req);

        verify(gateService, never()).assertPassed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("ISS-001: 跟踪总表内联编辑置 DONE 不再绕过完成硬标准")
    void shouldNotBypassCompletionCheckViaApplyCell() {
        ProjectNode node = node("PROD_TRIAL", 1, "IN_PROGRESS");
        node.setEvidenceCount(0);
        when(nodeMapper.selectOne(any())).thenReturn(node);

        // V = 完成；无证据 → 与表单路径同样被拒
        assertThrows(BusinessException.class, () -> service.applyCell(1L, "PROD_TRIAL", "V"));
        verify(nodeMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("ISS-001: 内联编辑置 DONE 且证据齐备时仍走关键节点 Gate")
    void shouldGateKeyNodeWhenApplyCellSetsDone() {
        ProjectNode node = node("PROD_TRIAL", 1, "IN_PROGRESS");
        node.setId(11L);
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);

        ProjectNode out = service.applyCell(1L, "PROD_TRIAL", "V");

        assertEquals("DONE", out.getStatus());
        assertNotNull(out.getActualDate());
        verify(gateService).assertPassed(eq(GateService.GATE_NODE_KEY_DONE), eq("node"), eq("11"));
    }

    @Test
    @DisplayName("F09: 节点证据上传同时登记统一证据底座(引用原始记录)")
    void shouldRegisterUnifiedEvidenceOnUpload() {
        ProjectNode node = node("TEST_REPORT", 1, "IN_PROGRESS");
        node.setId(9L);
        node.setProjectNo("HJ001");
        when(nodeMapper.selectById(9L)).thenReturn(node);
        PlmFile pf = new PlmFile();
        pf.setId(5L);
        pf.setFileName("HJ001_测试报告_V1.pdf");
        when(fileService.upload(any(), eq("HJ001"), any(), any())).thenReturn(pf);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);
        MultipartFile mf = mock(MultipartFile.class);

        service.uploadEvidence(9L, mf, "TEST_REPORT", "安规报告");

        ArgumentCaptor<EvidenceService.RegisterCommand> cap =
                ArgumentCaptor.forClass(EvidenceService.RegisterCommand.class);
        verify(evidenceService).register(cap.capture());
        EvidenceService.RegisterCommand cmd = cap.getValue();
        assertEquals("node", cmd.subjectType());
        assertEquals("9", cmd.subjectId());
        assertEquals("file", cmd.evidenceType());
        // 穿透链末端指向原始记录(文件 id)，而非证据行本身
        assertEquals("5", cmd.sourceRecordId());
        assertEquals("V1", cmd.version());
        assertNotNull(cmd.confidence());
        assertEquals("plm_project_node_evidence", cmd.legacyRefType());
    }

    @Test
    @DisplayName("上传证据: 写文件服务 + 记录 + 刷新证据数")
    void shouldUploadEvidence() {
        ProjectNode node = node("TEST_REPORT", 1, "IN_PROGRESS");
        node.setId(9L);
        node.setProjectNo("HJ001");
        when(nodeMapper.selectById(9L)).thenReturn(node);
        PlmFile pf = new PlmFile();
        pf.setId(5L);
        pf.setFileName("HJ001_测试报告_V1.pdf");
        when(fileService.upload(any(), eq("HJ001"), any(), any())).thenReturn(pf);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);
        MultipartFile mf = mock(MultipartFile.class);

        ProjectNodeEvidence ev = service.uploadEvidence(9L, mf, "TEST_REPORT", "安规报告");

        assertEquals(5L, ev.getFileId());
        assertEquals("HJ001_测试报告_V1.pdf", ev.getFileName());
        verify(evidenceMapper).insert(any(ProjectNodeEvidence.class));
        assertEquals(1, node.getEvidenceCount());
    }

    @Test
    @DisplayName("自检: 健康分 + 问题清单(HIGH: 证据不足/关键未闭环/逾期)")
    void shouldSelfCheck() {
        Project p = new Project();
        p.setId(1L);
        p.setProjectNo("HJ001");
        p.setProjectName("投光灯项目");
        p.setStatus("ACTIVE");
        when(projectMapper.selectById(1L)).thenReturn(p);

        List<ProjectNode> nodes = new ArrayList<>();
        ProjectNode doneNoEvidence = node("PLAN", 0, "DONE");   // 证据不足
        doneNoEvidence.setActualDate(LocalDate.now());
        doneNoEvidence.setEvidenceCount(0);
        ProjectNode keyNotDone = node("MOLD", 1, "IN_PROGRESS"); // 关键未闭环
        ProjectNode overdue = node("BOM", 0, "IN_PROGRESS");     // 逾期
        overdue.setPlanDate(LocalDate.now().minusDays(5));
        nodes.add(doneNoEvidence);
        nodes.add(keyNotDone);
        nodes.add(overdue);
        when(nodeMapper.selectList(any())).thenReturn(nodes);
        when(changeLogService.countFormalChanges("HJ001")).thenReturn(5L);

        Map<String, Object> res = service.selfCheck(1L);

        assertNotNull(res.get("score"));
        assertTrue((int) res.get("score") < 100);
        @SuppressWarnings("unchecked")
        Map<String, Object> bySev = (Map<String, Object>) res.get("bySeverity");
        assertTrue((long) bySev.get("HIGH") >= 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) res.get("issues");
        assertTrue(issues.stream().anyMatch(i -> i.get("message").toString().contains("完成证据不足")));
        assertTrue(issues.stream().anyMatch(i -> i.get("message").toString().contains("关键节点未闭环")));
        assertTrue(issues.stream().anyMatch(i -> i.get("message").toString().contains("逾期")));
    }

    @Test
    @DisplayName("F10: 自检口径来自 HealthScoreDef, 并暴露分子分母与变更分类")
    void shouldExposeCaliberAndCaliberDrivenMetrics() {
        Project p = new Project();
        p.setId(1L);
        p.setProjectNo("HJ002");
        p.setProjectName("面板灯项目");
        p.setStatus("ACTIVE");
        when(projectMapper.selectById(1L)).thenReturn(p);

        ProjectNode n1 = node("PLAN", 0, "DONE");
        n1.setActualDate(LocalDate.now());
        n1.setPlanDate(LocalDate.now().minusDays(1));
        n1.setEvidenceCount(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of(n1));
        when(changeLogService.countFormalChanges("HJ002")).thenReturn(0L);

        Map<String, Object> res = service.selfCheck(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) res.get("metrics");
        // 无正式变更 → CF 满分
        assertEquals(1.0, metrics.get("cf"));
        // CR 是「必填字段完整率」，其分母来自口径类而非节点总数
        assertTrue((long) metrics.get("requiredFields") > 0);
        assertEquals(0L, metrics.get("formalChanges"));
        @SuppressWarnings("unchecked")
        Map<String, Object> caliber = (Map<String, Object>) res.get("caliber");
        assertEquals("M_PROJECT_HEALTH", caliber.get("metricCode"));
        assertNotNull(caliber.get("collectionMethod"));
        assertNotNull(caliber.get("changeCategories"));
    }

    @Test
    @DisplayName("系统内填写证据: source=TEXT + 刷新证据数")
    void shouldCreateTextEvidence() {
        ProjectNode node = node("PLAN", 0, "IN_PROGRESS");
        node.setId(9L);
        when(nodeMapper.selectById(9L)).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNodeEvidence ev = service.createTextEvidence(9L, "PLAN", "计划说明", "1. 里程碑A 2026-10-01");

        assertEquals("TEXT", ev.getSource());
        assertTrue(ev.getFileName().contains("填写"));
        assertEquals("1. 里程碑A 2026-10-01", ev.getContent());
        verify(evidenceMapper).insert(any(ProjectNodeEvidence.class));
        assertEquals(1, node.getEvidenceCount());
    }

    @Test
    @DisplayName("填写证据: 内容为空拒绝")
    void shouldRejectEmptyTextEvidence() {
        ProjectNode node = node("PLAN", 0, "IN_PROGRESS");
        when(nodeMapper.selectById(9L)).thenReturn(node);
        assertThrows(BusinessException.class, () -> service.createTextEvidence(9L, "PLAN", "", "  "));
        verify(evidenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("证据分析: 来源/类型/总量统计")
    void shouldAggregateEvidenceStats() {
        ProjectNodeEvidence fileEv = new ProjectNodeEvidence();
        fileEv.setSource("FILE"); fileEv.setDocType("PLAN"); fileEv.setNodeCode("PLAN");
        fileEv.setUploadedBy("admin");
        ProjectNodeEvidence textEv = new ProjectNodeEvidence();
        textEv.setSource("TEXT"); textEv.setDocType("BOM"); textEv.setNodeCode("BOM");
        textEv.setUploadedBy("admin");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(fileEv, textEv));

        Map<String, Object> stats = service.evidenceStats(null);

        assertEquals(2, stats.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Long> bySource = (Map<String, Long>) stats.get("bySource");
        assertEquals(1L, bySource.get("FILE"));
        assertEquals(1L, bySource.get("TEXT"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byUploader = (Map<String, Long>) stats.get("byUploader");
        assertEquals(2L, byUploader.get("admin"));
    }

    private ProjectNode node(String code, int key, String status) {
        ProjectNode n = new ProjectNode();
        n.setId(1L);
        n.setProjectId(1L);
        n.setProjectNo("HJ001");
        n.setNodeCode(code);
        n.setNodeName(code);
        n.setIsKey(key);
        n.setStatus(status);
        n.setEvidenceCount(0);
        n.setEditCount(0);
        return n;
    }
}
