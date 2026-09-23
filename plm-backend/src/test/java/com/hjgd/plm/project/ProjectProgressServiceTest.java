package com.hjgd.plm.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.mapper.AeosEvidenceMapper;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeEvidence;
import com.hjgd.plm.project.mapper.ProjectChangeMapper;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeApprovalMapper;
import com.hjgd.plm.project.mapper.ProjectNodeEvidenceMapper;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.ProjectProgressService;
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
import static org.mockito.Mockito.*;

/**
 * 研发项目进度跟踪(小模型): 19 节点初始化 + 完成硬标准 + 自检健康分。
 */
@DisplayName("项目进度跟踪(小模型)")
@ExtendWith(MockitoExtension.class)
class ProjectProgressServiceTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectNodeMapper nodeMapper;
    @Mock private ProjectNodeEvidenceMapper evidenceMapper;
    @Mock private ProjectNodeApprovalMapper approvalMapper;
    @Mock private ProjectChangeMapper changeMapper;
    @Mock private AeosEvidenceMapper aeosEvidenceMapper;
    @Mock private FileService fileService;
    @InjectMocks private ProjectProgressService service;

    @Test
    @DisplayName("初始化: 每项目写入 19 个节点")
    void shouldInitNineteenNodes() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);

        service.initNodes(1L, "HJ001");

        ArgumentCaptor<ProjectNode> cap = ArgumentCaptor.forClass(ProjectNode.class);
        verify(nodeMapper, times(19)).insert(cap.capture());
        List<ProjectNode> nodes = cap.getAllValues();
        assertEquals("PLAN", nodes.get(0).getNodeCode());
        assertEquals("计划表", nodes.get(0).getNodeName());
        assertEquals("OTHER", nodes.get(18).getNodeCode());
        long keyCount = nodes.stream().filter(n -> n.getIsKey() == 1).count();
        assertEquals(7, keyCount, "关键节点应为 7 个");
        assertTrue(nodes.stream().allMatch(n -> "NOT_SET".equals(n.getStatus())));
    }

    @Test
    @DisplayName("完成硬标准: 缺实际日期 或 缺证据 均拒绝")
    void shouldEnforceCompletionStandard() {
        ProjectNode node = node("MOLD_REVIEW", 1, "NOT_SET");
        when(nodeMapper.selectOne(any())).thenReturn(node);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        // 缺实际日期
        assertTrue(assertThrows(BusinessException.class, () -> service.updateNode(1L, "MOLD_REVIEW", req))
                .getMessage().contains("实际完成日期"));

        // 有实际日期但无证据
        req.setActualDate(LocalDate.now());
        assertTrue(assertThrows(BusinessException.class, () -> service.updateNode(1L, "MOLD_REVIEW", req))
                .getMessage().contains("证据"));
    }

    @Test
    @DisplayName("普通节点完成硬标准满足: 实际日期 + 证据>=1 → 通过")
    void shouldCompleteWhenEvidencePresent() {
        ProjectNode node = node("BOM", 0, "IN_PROGRESS");
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        req.setActualDate(LocalDate.now());
        ProjectNode out = service.updateNode(1L, "BOM", req);

        assertEquals("DONE", out.getStatus());
        assertNotNull(out.getActualDate());
        verify(nodeMapper, atLeastOnce()).updateById(node);
    }

    @Test
    @DisplayName("关键节点禁止绕过双级审批直接DONE")
    void shouldRejectKeyNodeDoneWithoutApproval() {
        ProjectNode node = node("MOLD_REVIEW", 1, "IN_PROGRESS");
        node.setEvidenceCount(1);
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(approvalMapper.selectList(any())).thenReturn(List.of());

        ProjectNode req = new ProjectNode();
        req.setStatus("DONE");
        req.setActualDate(LocalDate.now());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateNode(1L, "MOLD_REVIEW", req));
        assertNotNull(ex.getMessage());
        verify(approvalMapper).selectList(any());
    }

    @Test
    @DisplayName("关键节点Evidence齐全后可提交RD_LEAD审批")
    void shouldSubmitKeyNodeApproval() {
        ProjectNode node = node("TEST_REPORT", 1, "IN_PROGRESS");
        node.setId(9L);
        node.setActualDate(LocalDate.now());
        node.setEvidenceCount(1);
        when(nodeMapper.selectById(9L)).thenReturn(node);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);
        when(approvalMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> out = service.submitApproval(9L, 100L, "提交人", "请审批", "REQ-001");

        assertEquals("READY_FOR_APPROVAL", node.getStatus());
        verify(approvalMapper).insert(any());
        verify(nodeMapper).updateById(node);
        assertEquals("READY_FOR_APPROVAL", out.get("nodeStatus"));
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
        assertNotNull(ev.getAeosEvidenceId());
        verify(evidenceMapper).insert(any(ProjectNodeEvidence.class));
        verify(aeosEvidenceMapper).insert(any());
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
        when(nodeMapper.selectCount(any())).thenReturn(19L); // 已初始化

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
        when(changeMapper.selectCount(any())).thenReturn(0L);

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
        assertNotNull(ev.getAeosEvidenceId());
        verify(evidenceMapper).insert(any(ProjectNodeEvidence.class));
        verify(aeosEvidenceMapper).insert(any());
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
