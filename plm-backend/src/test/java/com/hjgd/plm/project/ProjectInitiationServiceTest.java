package com.hjgd.plm.project;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectGateLog;
import com.hjgd.plm.project.entity.ProjectInitiation;
import com.hjgd.plm.project.mapper.ProjectGateLogMapper;
import com.hjgd.plm.project.mapper.ProjectInitiationMapper;
import com.hjgd.plm.project.service.ProjectInitiationService;
import com.hjgd.plm.project.service.ProjectService;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 研发项目立项申请书: 五阶段审批流 + 批准转项目。
 */
@DisplayName("立项申请书 五阶段审批流")
@ExtendWith(MockitoExtension.class)
class ProjectInitiationServiceTest {

    @Mock private ProjectInitiationMapper initiationMapper;
    @Mock private ProjectGateLogMapper gateLogMapper;
    @Mock private ProjectService projectService;
    @Mock private SequenceService sequenceService;
    @InjectMocks private ProjectInitiationService service;

    private void loginAs(String... roles) {
        LoginUser u = mock(LoginUser.class);
        lenient().when(u.getRealName()).thenReturn("张三");
        lenient().when(u.getRoles()).thenReturn(List.of(roles));
        lenient().when(u.getUserId()).thenReturn(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, Collections.emptyList()));
    }

    @BeforeEach
    void setUp() { loginAs("ADMIN"); }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private ProjectInitiation rec(String stage, String status) {
        ProjectInitiation r = new ProjectInitiation();
        r.setId(1L);
        r.setInitNo("LX202609160001");
        r.setProjectName("投光灯新项目");
        r.setApprovalStatus(status);
        r.setWorkflowStage(stage);
        return r;
    }

    @Test
    @DisplayName("创建: 生成编号 + draft/apply + 申请人/日期")
    void shouldCreateWithDefaults() {
        when(sequenceService.nextNo("INITIATION_NO")).thenReturn("LX202609160001");
        ProjectInitiation r = new ProjectInitiation();
        r.setProjectName("投光灯新项目");

        service.create(r);

        assertEquals("LX202609160001", r.getInitNo());
        assertEquals("draft", r.getApprovalStatus());
        assertEquals("apply", r.getWorkflowStage());
        assertEquals("张三", r.getApplicant());
        assertNotNull(r.getApplyDate());
        assertEquals("研发中心", r.getDepartment());
        verify(initiationMapper).insert(r);
    }

    @Test
    @DisplayName("推进 apply→dept: 状态转 submitted")
    void shouldAdvanceFromApply() {
        ProjectInitiation r = rec("apply", "draft");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        ProjectInitiationService.AdvanceReq req = new ProjectInitiationService.AdvanceReq();
        req.setReviewer("张三");
        service.advance(1L, req);

        assertEquals("dept", r.getWorkflowStage());
        assertEquals("submitted", r.getApprovalStatus());
        assertNotNull(r.getStep1ApplyDate());
        verify(initiationMapper).updateById(r);
    }

    @Test
    @DisplayName("总经理批准通过: approved + 推进至 execute")
    void shouldApproveAtGmStage() {
        ProjectInitiation r = rec("gm", "submitted");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        ProjectInitiationService.AdvanceReq req = new ProjectInitiationService.AdvanceReq();
        req.setOpinion("同意立项");
        service.advance(1L, req);

        assertEquals("execute", r.getWorkflowStage());
        assertEquals("approved", r.getApprovalStatus());
        assertEquals("同意立项", r.getStep4Opinion());
        verify(initiationMapper, times(1)).updateById(r);
    }

    @Test
    @DisplayName("总经理驳回: rejected")
    void shouldRejectAtGmStage() {
        ProjectInitiation r = rec("gm", "submitted");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        ProjectInitiationService.AdvanceReq req = new ProjectInitiationService.AdvanceReq();
        req.setResult("reject");
        req.setOpinion("预算过高");
        service.advance(1L, req);

        assertEquals("rejected", r.getWorkflowStage());
        assertEquals("rejected", r.getApprovalStatus());
        assertEquals("预算过高", r.getApprovalOpinion());
    }

    @Test
    @DisplayName("非总经理角色不可批准")
    void shouldDenyGmStageForEngineer() {
        loginAs("ENGINEER");
        ProjectInitiation r = rec("gm", "submitted");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class,
                () -> service.advance(1L, new ProjectInitiationService.AdvanceReq()));
    }

    @Test
    @DisplayName("批准转项目: 创建 plm_project + 回填 project_id + G0门记录")
    void shouldApproveToProject() {
        ProjectInitiation r = rec("gm", "submitted");
        r.setCustomerNo("CUS-001");
        r.setCustomerLevel("A");
        r.setOwner("李四");
        r.setBudgetTotal(java.math.BigDecimal.valueOf(50000));
        when(initiationMapper.selectById(1L)).thenReturn(r);
        when(projectService.create(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(88L);
            return p;
        });

        Map<String, Object> out = service.approveToProject(1L, null);

        assertEquals(88L, out.get("projectId"));
        assertEquals(88L, r.getProjectId());
        assertEquals("approved", r.getApprovalStatus());
        verify(gateLogMapper).insert(any(ProjectGateLog.class));
        verify(initiationMapper, atLeastOnce()).updateById(r);
    }

    @Test
    @DisplayName("已批准的申请书不可修改")
    void shouldRejectUpdateWhenApproved() {
        ProjectInitiation r = rec("execute", "approved");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        ProjectInitiation patch = new ProjectInitiation();
        patch.setId(1L);
        patch.setProjectName("改名");

        assertThrows(BusinessException.class, () -> service.update(patch));
        verify(initiationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("已是最终阶段不可再推进")
    void shouldRejectAdvanceAtFinalStage() {
        ProjectInitiation r = rec("execute", "approved");
        when(initiationMapper.selectById(1L)).thenReturn(r);

        assertThrows(BusinessException.class,
                () -> service.advance(1L, new ProjectInitiationService.AdvanceReq()));
    }
}
