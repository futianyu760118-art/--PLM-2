package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.PlmDecision;
import com.hjgd.plm.deh.mapper.PlmDecisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DEH 动作（F16 / DEH_CONTRACT_V1 §4、§8）。
 *
 * <p>动作是穿透链第 3 段：{@code decision_id} 指回决策，
 * 由 {@link ResultService} 挂结果（{@code result_id}），
 * 从而形成「决策 → 动作 → 结果」的连续链。
 *
 * <p>DEH §4 要求 Action 必须有 <b>五要素</b>：Owner、期限、状态、验证规则、回退/升级路径。
 * {@link #create} 对缺项直接拒绝 —— 一条没有 Owner 或没有期限的动作，
 * 事后无法判定「谁该在什么时候做完」，也就无从验证。
 *
 * <p>状态机（DEH §8）：{@code candidate → confirmed → in_progress → done}，
 * 异常分支 {@code blocked / cancelled}。{@code done} 仅表示执行者声明完成，
 * 是否被接受由结果侧 {@code verified} 决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionService {

    /** 合法状态迁移表（含旧值 APPROVED，兼容迁移期数据）。 */
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "candidate",   List.of("confirmed", "cancelled"),
            "confirmed",   List.of("in_progress", "blocked", "cancelled"),
            "in_progress", List.of("done", "blocked", "cancelled"),
            "blocked",     List.of("in_progress", "cancelled"),
            "APPROVED",    List.of("in_progress", "blocked", "done", "cancelled")
    );

    private final AgentActionMapper actionMapper;
    private final PlmDecisionMapper decisionMapper;

    /** 动作创建入参（DEH §4）。 */
    public record CreateCommand(
            String decisionId, String sourceType, String sourceId,
            String objectType, String objectId, String description,
            String expectedResult, String ownerId, String ownerRole,
            String priority, OffsetDateTime dueAt, String verificationRule,
            String escalationRule, Boolean evidenceRequired) {
    }

    /**
     * 建立动作（candidate）。五要素缺一即拒绝。
     *
     * @throws BusinessException Owner / 期限 / 验证规则 / 回退升级路径缺失
     */
    @Transactional
    public AgentAction create(CreateCommand cmd) {
        if (cmd == null) throw invalid("动作入参为空");
        requireText(cmd.objectType(), "object_ref.object_type");
        requireText(cmd.objectId(), "object_ref.object_id");
        requireText(cmd.description(), "description");
        requireText(cmd.ownerId(), "owner_id");
        if (cmd.dueAt() == null) throw invalid("due_at 不得为空：动作必须有期限（DEH §4）");
        requireText(cmd.verificationRule(), "verification_rule（怎么算做完）");
        requireText(cmd.escalationRule(), "escalation_rule（回退/升级路径）");

        if (StringUtils.hasText(cmd.decisionId())) {
            PlmDecision d = decisionMapper.selectById(cmd.decisionId());
            if (d == null) throw new BusinessException(404, "决策不存在: " + cmd.decisionId());
            if (!"confirmed".equals(d.getStatus())) {
                throw invalid("决策[" + cmd.decisionId() + "]状态为 " + d.getStatus()
                        + "，未确认的决策不得派生动作");
            }
        }

        AgentAction a = new AgentAction();
        a.setActionId("ACT-" + UUID.randomUUID());
        a.setDecisionId(cmd.decisionId());
        a.setSourceType(cmd.sourceType());
        a.setSourceId(cmd.sourceId());
        a.setObjectType(cmd.objectType());
        a.setObjectId(cmd.objectId());
        a.setActionType("DEH");
        a.setDescription(cmd.description());
        a.setExpectedResult(DehJson.value(cmd.expectedResult()));
        a.setOwnerId(cmd.ownerId());
        a.setOwnerRole(cmd.ownerRole());
        a.setPriority(StringUtils.hasText(cmd.priority()) ? cmd.priority() : "medium");
        a.setDueAt(cmd.dueAt());
        a.setVerificationRule(DehJson.value(cmd.verificationRule()));
        a.setEscalationRule(DehJson.value(cmd.escalationRule()));
        a.setEvidenceRequired(Boolean.TRUE.equals(cmd.evidenceRequired()));
        a.setStatus("candidate");
        a.setCreatedAt(OffsetDateTime.now());
        actionMapper.insert(a);
        log.info("[deh] action created {} decision={} owner={}", a.getActionId(), cmd.decisionId(), cmd.ownerId());
        return a;
    }

    /**
     * 状态迁移。非法迁移（如 candidate 直接 done）拒绝并说明合法去向。
     *
     * @throws BusinessException 目标状态非法 / 当前状态不允许该迁移
     */
    @Transactional
    public AgentAction transition(String actionId, String toStatus) {
        requireText(toStatus, "目标状态");
        AgentAction a = get(actionId);
        List<String> allowed = TRANSITIONS.getOrDefault(a.getStatus(), List.of());
        if (!allowed.contains(toStatus)) {
            throw invalid("动作[" + actionId + "]不允许 " + a.getStatus() + " → " + toStatus
                    + "，当前状态可迁移到 " + allowed);
        }
        a.setStatus(toStatus);
        if ("in_progress".equals(toStatus) && a.getStartAt() == null) {
            a.setStartAt(OffsetDateTime.now());
        }
        if ("done".equals(toStatus)) {
            a.setDecidedAt(OffsetDateTime.now());
        }
        actionMapper.updateById(a);
        return a;
    }

    public AgentAction get(String actionId) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        w.eq(AgentAction::getActionId, actionId).last("LIMIT 1");
        List<AgentAction> list = actionMapper.selectList(w);
        if (list.isEmpty()) throw new BusinessException(404, "动作不存在: " + actionId);
        return list.get(0);
    }

    /** 按决策列动作（穿透链第 2→3 段：决策 → 动作）。 */
    public List<AgentAction> listByDecision(String decisionId) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        w.eq(AgentAction::getDecisionId, decisionId).orderByAsc(AgentAction::getId);
        return actionMapper.selectList(w);
    }

    /** 按对象列动作。 */
    public List<AgentAction> listByObject(String objectType, String objectId) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        w.eq(AgentAction::getObjectType, objectType)
                .eq(AgentAction::getObjectId, objectId)
                .orderByDesc(AgentAction::getId);
        return actionMapper.selectList(w);
    }

    /**
     * 逾期检查（DEH §8 异常路径 overdue）。
     * 逾期是「期限已过且未达终态」的派生事实，不落库 —— 落库会随时间失真。
     */
    public Map<String, Object> overdueOf(String actionId) {
        AgentAction a = get(actionId);
        boolean terminal = List.of("done", "cancelled", "APPROVED").contains(a.getStatus());
        boolean overdue = !terminal && a.getDueAt() != null
                && a.getDueAt().isBefore(OffsetDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("actionId", actionId);
        out.put("status", a.getStatus());
        out.put("dueAt", a.getDueAt());
        out.put("overdue", overdue);
        return out;
    }

    private String currentUser() {
        try {
            String name = SecurityUtils.getCurrentRealName();
            if (StringUtils.hasText(name)) return name;
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private static void requireText(String v, String field) {
        if (!StringUtils.hasText(v)) throw invalid(field + " 不得为空");
    }

    private static BusinessException invalid(String msg) {
        return new BusinessException(422, "[" + ApiErrorCodes.DECISION_INVALID + "] " + msg);
    }
}
