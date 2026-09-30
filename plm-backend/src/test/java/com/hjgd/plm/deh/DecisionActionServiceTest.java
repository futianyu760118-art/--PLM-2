package com.hjgd.plm.deh;

import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.PlmDecision;
import com.hjgd.plm.deh.mapper.PlmDecisionMapper;
import com.hjgd.plm.deh.service.ActionService;
import com.hjgd.plm.deh.service.DecisionService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F16 DEH 决策与动作：决策可回溯、动作五要素齐备方可成立。
 */
@DisplayName("F16 DEH 决策与动作")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionActionServiceTest {

    @Mock private PlmDecisionMapper decisionMapper;
    @InjectMocks private DecisionService decisionService;

    @Mock private AgentActionMapper actionMapper;
    @InjectMocks private ActionService actionService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String realName) {
        SysUser u = new SysUser();
        u.setUsername(realName.toLowerCase());
        u.setRealName(realName);
        LoginUser lu = new LoginUser(u, List.of("ENGINEER"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    /**
     * 决策来源按 DEH §3 填：source_type 是触发来源的种类（指标系统报出 → system），
     * 被指向的业务对象（指标）挂在 object_ref 上。
     */
    private DecisionService.CreateCommand decisionCmd(String sourceType, String sourceId) {
        return new DecisionService.CreateCommand(sourceType, sourceId, "metric", "METRIC-001",
                "CF 口径改为正式项目变更次数", "节点编辑次数可被刷，口径失真", "业务与产品负责人",
                null, null, null);
    }

    private ActionService.CreateCommand actionCmd(String decisionId, boolean withOwner,
                                                  boolean withDue, boolean withVerification,
                                                  boolean withEscalation) {
        return new ActionService.CreateCommand(
                decisionId, "metric", "METRIC-001", "metric", "METRIC-001",
                "改 ProjectProgressService CF 计算 + 口径迁移",
                "{\"cf\":\"formal_change_count\"}",
                withOwner ? "zhangsan" : null,
                "研发工程师", "high",
                withDue ? OffsetDateTime.now().plusDays(7) : null,
                withVerification ? "{\"rule\":\"定义式计算值=实现值\"}" : null,
                withEscalation ? "{\"rule\":\"超期升级至研发主管\"}" : null,
                Boolean.TRUE);
    }

    private PlmDecision decision(String id, String status) {
        PlmDecision d = new PlmDecision();
        d.setDecisionId(id);
        d.setStatus(status);
        d.setSourceType("metric");
        d.setSourceId("METRIC-001");
        return d;
    }

    // ---------------------------------------------------------------- 决策

    @Test
    @DisplayName("建立决策：source 引用齐备时落 draft，业务对象记在 object_ref")
    void createDecision() {
        loginAs("架构师");
        PlmDecision d = decisionService.create(decisionCmd("system", "METRIC-RULE-7"));

        assertEquals("draft", d.getStatus());
        assertTrue(d.getDecisionId().startsWith("DEC-"));
        assertEquals("system", d.getSourceType());
        assertEquals("METRIC-RULE-7", d.getSourceId());
        assertEquals("metric", d.getObjectType());
        assertEquals("METRIC-001", d.getObjectId());
        verify(decisionMapper).insert(any(PlmDecision.class));
    }

    @Test
    @DisplayName("source_type 非法 → 拒绝，且不许把业务对象类型当 source_type 混进来")
    void createDecisionRejectsBadSourceType() {
        loginAs("架构师");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                decisionService.create(decisionCmd("telepathy", "X")));
        assertTrue(ex.getMessage().contains("source_type"));

        // metric 是业务对象类型，按 DEH §3 只能进 object_ref，不能进 source_type
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                decisionService.create(decisionCmd("metric", "METRIC-001")));
        assertTrue(ex2.getMessage().contains("source_type"));
    }

    @Test
    @DisplayName("source_id 缺失 → 拒绝")
    void createDecisionRejectsMissingSourceId() {
        loginAs("架构师");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                decisionService.create(decisionCmd("system", "  ")));
        assertTrue(ex.getMessage().contains("source_id"));
    }

    @Test
    @DisplayName("确认决策：draft→confirmed 并记录决策时点")
    void confirmDecision() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "draft"));

        PlmDecision d = decisionService.confirm("DEC-1");

        assertEquals("confirmed", d.getStatus());
        assertNotNull(d.getDecidedAt());
    }

    @Test
    @DisplayName("已确认的决策不可直接取消，须走 supersede 保留历史")
    void cannotCancelConfirmedDecision() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "confirmed"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                decisionService.cancel("DEC-1", "算了"));
        assertTrue(ex.getMessage().contains("supersede"));
    }

    @Test
    @DisplayName("取代决策不改写历史：置 superseded 并保留原结论")
    void supersedeKeepsHistory() {
        loginAs("研发主管");
        PlmDecision d = decision("DEC-1", "confirmed");
        d.setReason("原口径失真");
        when(decisionMapper.selectById("DEC-1")).thenReturn(d);

        PlmDecision out = decisionService.supersede("DEC-1", "口径再次调整");

        assertEquals("superseded", out.getStatus());
        assertTrue(out.getReason().contains("原口径失真"), "原结论必须保留");
        assertTrue(out.getReason().contains("口径再次调整"));
    }

    // ---------------------------------------------------------------- 动作

    @Test
    @DisplayName("五要素齐备时动作成立，初始状态 candidate")
    void createActionWithFiveElements() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "confirmed"));

        AgentAction a = actionService.create(actionCmd("DEC-1", true, true, true, true));

        assertEquals("candidate", a.getStatus());
        assertTrue(a.getActionId().startsWith("ACT-"));
        assertEquals("DEC-1", a.getDecisionId());
        assertEquals("zhangsan", a.getOwnerId());
        assertNotNull(a.getDueAt());
    }

    @Test
    @DisplayName("缺 Owner → 动作不成立")
    void actionRejectedWithoutOwner() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "confirmed"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.create(actionCmd("DEC-1", false, true, true, true)));
        assertTrue(ex.getMessage().contains("owner_id"));
    }

    @Test
    @DisplayName("缺期限 → 动作不成立：事后无法判定该在何时做完")
    void actionRejectedWithoutDueDate() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "confirmed"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.create(actionCmd("DEC-1", true, false, true, true)));
        assertTrue(ex.getMessage().contains("due_at"));
    }

    @Test
    @DisplayName("缺验证规则 / 回退升级路径 → 动作不成立")
    void actionRejectedWithoutVerificationOrEscalation() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "confirmed"));

        assertTrue(assertThrows(BusinessException.class, () ->
                actionService.create(actionCmd("DEC-1", true, true, false, true)))
                .getMessage().contains("verification_rule"));
        assertTrue(assertThrows(BusinessException.class, () ->
                actionService.create(actionCmd("DEC-1", true, true, true, false)))
                .getMessage().contains("escalation_rule"));
    }

    @Test
    @DisplayName("未确认的决策不得派生动作")
    void actionRejectedWhenDecisionNotConfirmed() {
        loginAs("研发主管");
        when(decisionMapper.selectById("DEC-1")).thenReturn(decision("DEC-1", "draft"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.create(actionCmd("DEC-1", true, true, true, true)));
        assertTrue(ex.getMessage().contains("未确认"));
    }

    @Test
    @DisplayName("状态机：candidate→confirmed→in_progress→done，起点即 in_progress 被挡")
    void transitionFollowsStateMachine() {
        loginAs("研发主管");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-1", "candidate")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.transition("ACT-1", "done"));
        assertTrue(ex.getMessage().contains("不允许"));
    }

    @Test
    @DisplayName("确认后可开工，开工记录 start_at")
    void transitionToInProgressSetsStart() {
        loginAs("研发主管");
        when(actionMapper.selectList(any())).thenReturn(List.of(action("ACT-1", "confirmed")));

        AgentAction a = actionService.transition("ACT-1", "in_progress");

        assertEquals("in_progress", a.getStatus());
        assertNotNull(a.getStartAt());
    }

    @Test
    @DisplayName("逾期是派生事实：期限已过且未终态")
    void overdueIsDerived() {
        loginAs("研发主管");
        AgentAction overdue = action("ACT-1", "in_progress");
        overdue.setDueAt(OffsetDateTime.now().minusDays(1));
        when(actionMapper.selectList(any())).thenReturn(List.of(overdue));

        Map<String, Object> out = actionService.overdueOf("ACT-1");
        assertEquals(Boolean.TRUE, out.get("overdue"));

        AgentAction done = action("ACT-2", "done");
        done.setDueAt(OffsetDateTime.now().minusDays(1));
        when(actionMapper.selectList(any())).thenReturn(List.of(done));
        assertEquals(Boolean.FALSE, actionService.overdueOf("ACT-2").get("overdue"),
                "已完成的动作不算逾期");
    }

    private AgentAction action(String actionId, String status) {
        AgentAction a = new AgentAction();
        a.setId(1L);
        a.setActionId(actionId);
        a.setStatus(status);
        return a;
    }
}
