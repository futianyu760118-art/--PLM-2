package com.hjgd.plm.deh;

import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.AgentResult;
import com.hjgd.plm.deh.mapper.AgentResultMapper;
import com.hjgd.plm.deh.service.ResultService;
import com.hjgd.plm.system.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F16 结果与独立验证：{@code done ≠ verified} 必须真的挡得住。
 */
@DisplayName("F16 DEH 结果验证")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultServiceTest {

    @Mock private AgentResultMapper resultMapper;
    @Mock private AgentActionMapper actionMapper;
    @InjectMocks private ResultService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 以真实 SecurityContext 模拟登录用户（服务读的是当前用户，测试就走同一条路径）。 */
    private void loginAs(String username, String realName) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setRealName(realName);
        LoginUser lu = new LoginUser(u, List.of("ENGINEER"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    private AgentAction action(String actionId, String status) {
        AgentAction a = new AgentAction();
        a.setId(7L);
        a.setActionId(actionId);
        a.setStatus(status);
        a.setObjectType("metric");
        a.setObjectId("METRIC-001");
        return a;
    }

    private AgentResult result(String resultId, String status, String submittedBy) {
        AgentResult r = new AgentResult();
        r.setResultId(resultId);
        r.setActionId("ACT-2026-014");
        r.setStatus(status);
        r.setSubmittedBy(submittedBy);
        return r;
    }

    // ---------------------------------------------------------------- 提交

    @Test
    @DisplayName("提交结果只置 submitted —— 执行者声明完成不等于 AEOS 接受")
    void submitLeavesResultUnverified() {
        loginAs("alice", "Alice");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-2026-014", "in_progress")));

        AgentResult r = service.submit(new ResultService.SubmitCommand(
                "ACT-2026-014", null, null, "CF=正式变更次数", "{\"cf\":3}", null));

        assertEquals("submitted", r.getStatus(), "提交后必须是 submitted，不得直接 verified");
        assertNull(r.getVerifiedBy(), "提交者不能顺带把自己验证了");
        assertNull(r.getVerifiedAt());
        assertEquals("Alice", r.getSubmittedBy());
        verify(resultMapper).insert(any(AgentResult.class));
    }

    @Test
    @DisplayName("写库前规整成 JSON：纯文本与证据引用不得裸奔进 JSONB 列")
    void submitNormalizesJsonColumns() {
        loginAs("alice", "Alice");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-2026-014", "in_progress")));

        AgentResult r = service.submit(new ResultService.SubmitCommand(
                "ACT-2026-014", null, null, "CF 改为正式变更次数", "CF 由 11 降到 3", "E-004,E-005"));

        assertEquals("\"CF 由 11 降到 3\"", r.getActualResult(), "纯文本要编码成 JSON 字符串");
        assertEquals("\"CF 改为正式变更次数\"", r.getExpectedResult());
        assertEquals("[\"E-004\",\"E-005\"]", r.getEvidenceRefs(), "证据引用是 JSON 数组，不是裸字符串");
    }

    @Test
    @DisplayName("验证时写的证据引用同样规整成 JSON 数组")
    void verifyNormalizesEvidenceRefs() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        AgentResult r = service.verify("RES-1", "E-005, E-006", null);

        assertEquals("[\"E-005\",\"E-006\"]", r.getEvidenceRefs());
    }

    @Test
    @DisplayName("提交后回写动作的 result_id —— 动作与结果双向可查")
    void submitBackfillsActionResultId() {
        loginAs("alice", "Alice");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-2026-014", "in_progress")));

        AgentResult r = service.submit(new ResultService.SubmitCommand(
                "ACT-2026-014", null, null, null, "{\"cf\":3}", null));

        verify(actionMapper).updateById(argThat((AgentAction a) ->
                Long.valueOf(7L).equals(a.getId()) && r.getResultId().equals(a.getResultId())));
    }

    @Test
    @DisplayName("动作未确认时不得提交结果")
    void submitRejectedWhenActionNotConfirmed() {
        loginAs("alice", "Alice");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-1", "candidate")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(new ResultService.SubmitCommand("ACT-1", null, null, null, "x", null)));
        assertTrue(ex.getMessage().contains("DECISION_INVALID"));
        assertTrue(ex.getMessage().contains("candidate"));
    }

    @Test
    @DisplayName("actual_result 为空不得提交")
    void submitRejectedWithoutActualResult() {
        loginAs("alice", "Alice");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(new ResultService.SubmitCommand("ACT-1", null, null, null, "  ", null)));
        assertTrue(ex.getMessage().contains("actual_result"));
    }

    @Test
    @DisplayName("action_id 为空不得提交 —— 结果必须挂在动作上")
    void submitRejectedWithoutActionId() {
        loginAs("alice", "Alice");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(new ResultService.SubmitCommand(null, null, null, null, "x", null)));
        assertTrue(ex.getMessage().contains("action_id"));
    }

    @Test
    @DisplayName("动作不存在 → 404")
    void submitUnknownAction() {
        loginAs("alice", "Alice");
        when(actionMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(new ResultService.SubmitCommand("ACT-X", null, null, null, "x", null)));
        assertEquals(404, ex.getCode());
    }

    // ---------------------------------------------------------------- 验证

    @Test
    @DisplayName("独立验证通过：验证人 ≠ 提交人且带证据 → verified")
    void verifyByIndependentUser() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        AgentResult r = service.verify("RES-1", "plm_evidence:E-005", null);

        assertEquals("verified", r.getStatus());
        assertEquals("Bob", r.getVerifiedBy());
        assertNotNull(r.getVerifiedAt());
    }

    @Test
    @DisplayName("提交人自己不能验证自己 —— 非独立验证必须被挡")
    void verifyRejectedWhenVerifierIsSubmitter() {
        loginAs("alice", "Alice");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.verify("RES-1", "plm_evidence:E-005", null));
        assertTrue(ex.getMessage().contains("RESULT_VERIFY_DENIED"));
        assertTrue(ex.getMessage().contains("非独立验证"));
        verify(resultMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("既无验证证据又无豁免原因 → 拒绝")
    void verifyRejectedWithoutEvidenceOrExemption() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.verify("RES-1", null, null));
        assertTrue(ex.getMessage().contains("证据"));
        verify(resultMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("无证据但有明确豁免原因 → 放行，且豁免被记录")
    void verifyWithExemptionReason() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        AgentResult r = service.verify("RES-1", null, "纯文档改动，无需测试证据（已获品保豁免）");

        assertEquals("verified", r.getStatus());
        assertEquals("纯文档改动，无需测试证据（已获品保豁免）", r.getVerificationNote());
    }

    @Test
    @DisplayName("重复验证二次确认被挡")
    void verifyTwiceRejected() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "verified", "Alice"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.verify("RES-1", "ev", null));
        assertTrue(ex.getMessage().contains("不可验证"));
    }

    @Test
    @DisplayName("驳回保留原因，动作方可返工重交")
    void rejectKeepsReason() {
        loginAs("bob", "Bob");
        when(resultMapper.selectById("RES-1")).thenReturn(result("RES-1", "submitted", "Alice"));

        AgentResult r = service.reject("RES-1", "CF 口径实现与定义式不一致");

        assertEquals("rejected", r.getStatus());
        assertEquals("CF 口径实现与定义式不一致", r.getVerificationNote());
    }
}
