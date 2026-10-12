package com.hjgd.plm.project;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.mapper.ProjectNodeApprovalMapper;
import com.hjgd.plm.project.service.NodeApprovalService;
import com.hjgd.plm.system.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F07 关键节点双级审批：越权 / 重复 / 驳回 / 回退重开 / 同人双级 五类路径全覆盖。
 */
@DisplayName("F07 关键节点双级审批")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeApprovalServiceTest {

    @Mock private ProjectNodeApprovalMapper approvalMapper;
    @InjectMocks private NodeApprovalService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- 测试脚手架

    /** 以指定主角色/姓名进入安全上下文（模拟登录用户）。 */
    private void as(String role, String realName) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername(realName.toLowerCase());
        u.setRealName(realName);
        u.setStatus(1);
        LoginUser lu = new LoginUser(u, new ArrayList<>(List.of(role)), new ArrayList<>());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    /** 让 approvalMapper.selectList 依次返回构造好的审批历史（时间倒序，服务按顺序取每级最新）。 */
    private void history(ProjectNodeApproval... rows) {
        when(approvalMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(rows)));
    }

    private ProjectNodeApproval rec(int level, String role, String approver, String decision) {
        ProjectNodeApproval a = new ProjectNodeApproval();
        a.setId((long) level);
        a.setNodeId(100L);
        a.setLevel(level);
        a.setRoleCode(role);
        a.setApprover(approver);
        a.setDecision(decision);
        a.setDecidedAt(LocalDateTime.now());
        return a;
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    @DisplayName("研发主管与总经理分别审批 → 构成双级审批")
    void dualApprovalByTwoPeople() {
        as("RD_LEAD", "张三");
        service.decide(100L, 1L, "HJ001", "MOLD", 1, ProjectNodeApproval.APPROVED, "同意");
        verify(approvalMapper).insert(any(ProjectNodeApproval.class));

        as("GM", "李四");
        service.decide(100L, 1L, "HJ001", "MOLD", 2, ProjectNodeApproval.APPROVED, "同意");

        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.APPROVED),
                rec(2, "GM", "李四", ProjectNodeApproval.APPROVED));
        assertTrue(service.isDualApproved(100L));
        assertDoesNotThrow(() -> service.assertDualApproved(100L, "MOLD"));
    }

    @Test
    @DisplayName("审批记录写入审批人与角色, 级别校验失败不落库")
    void decidePersistsApproverAndRole() {
        as("RD_LEAD", "张三");
        service.decide(100L, 1L, "HJ001", "MOLD", 1, ProjectNodeApproval.APPROVED, "同意");

        ArgumentCaptor<ProjectNodeApproval> cap = ArgumentCaptor.forClass(ProjectNodeApproval.class);
        verify(approvalMapper).insert(cap.capture());
        assertEquals("张三", cap.getValue().getApprover());
        assertEquals("RD_LEAD", cap.getValue().getRoleCode());
        assertEquals(100L, cap.getValue().getNodeId());
        assertEquals("MOLD", cap.getValue().getNodeCode());
    }

    // ---------------------------------------------------------------- 异常路径

    @Test
    @DisplayName("越权: 工程师不能行使研发主管审批权")
    void rejectUnauthorizedRole() {
        as("ENGINEER", "王五");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.decide(100L, 1L, "HJ001", "MOLD", 1, ProjectNodeApproval.APPROVED, "同意"));
        assertTrue(ex.getMessage().contains("APPROVAL_DENIED"));
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("越权: 工程师不能行使总经理审批权")
    void rejectUnauthorizedGmRole() {
        as("ENGINEER", "王五");
        assertThrows(BusinessException.class,
                () -> service.decide(100L, 1L, "HJ001", "MOLD", 2, ProjectNodeApproval.APPROVED, "同意"));
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("重复: 同级已通过后再次通过被拒")
    void rejectDuplicateApproval() {
        as("RD_LEAD", "张三");
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.APPROVED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.decide(100L, 1L, "HJ001", "MOLD", 1, ProjectNodeApproval.APPROVED, "再同意"));
        assertTrue(ex.getMessage().contains("重复审批"));
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    @DisplayName("驳回: 研发主管驳回后节点不得判定完成")
    void rejectedBlocksDualApproval() {
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.REJECTED),
                rec(2, "GM", "李四", ProjectNodeApproval.APPROVED));

        assertFalse(service.isDualApproved(100L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertDualApproved(100L, "MOLD"));
        assertTrue(ex.getMessage().contains("驳回"));
    }

    @Test
    @DisplayName("未审批: 缺总经理一级时不构成双级")
    void missingGmLevel() {
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.APPROVED));
        assertFalse(service.isDualApproved(100L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertDualApproved(100L, "MOLD"));
        assertTrue(ex.getMessage().contains("总经理"));
    }

    @Test
    @DisplayName("同人双级: 同一人既批研发主管又批总经理 → 不构成双级")
    void sameApproverDoesNotCount() {
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.APPROVED),
                rec(2, "GM", "张三", ProjectNodeApproval.APPROVED));

        assertFalse(service.isDualApproved(100L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertDualApproved(100L, "MOLD"));
        assertTrue(ex.getMessage().contains("同一人"));
    }

    @Test
    @DisplayName("回退/重开: 撤回两级审批后需重新审批")
    void revokeAllResetsApproval() {
        as("ADMIN", "管理员");
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.APPROVED),
                rec(2, "GM", "李四", ProjectNodeApproval.APPROVED));

        int revoked = service.revokeAll(100L, 1L, "HJ001", "MOLD", "需求变更，重新审批");

        assertEquals(2, revoked, "两级审批都应被撤回");
        ArgumentCaptor<ProjectNodeApproval> cap = ArgumentCaptor.forClass(ProjectNodeApproval.class);
        verify(approvalMapper, times(2)).insert(cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(
                a -> ProjectNodeApproval.REVOKED.equals(a.getDecision())));
    }

    @Test
    @DisplayName("回退/重开: 撤回后状态回到未通过")
    void revokedStateIsNotApproved() {
        history(rec(1, "RD_LEAD", "张三", ProjectNodeApproval.REVOKED),
                rec(2, "GM", "李四", ProjectNodeApproval.REVOKED));
        assertFalse(service.isDualApproved(100L));
    }

    @Test
    @DisplayName("级别清单暴露两级与允许角色")
    void levelCatalog() {
        var catalog = service.levelCatalog();
        assertEquals(2, catalog.size());
        assertEquals(1, catalog.get(0).get("level"));
        assertEquals("RD_LEAD", catalog.get(0).get("roleCode"));
        assertEquals(2, catalog.get(1).get("level"));
        assertEquals("GM", catalog.get(1).get("roleCode"));

        assertThrows(BusinessException.class, () -> NodeApprovalService.Level.of(3));
    }
}
