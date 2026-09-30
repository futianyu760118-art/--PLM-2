package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.AgentResult;
import com.hjgd.plm.deh.mapper.AgentResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DEH 结果与验证（F16 / DEH_CONTRACT_V1 §5、§8）。
 *
 * <p>本服务是「{@code done ≠ verified}」的执行点：
 * <ul>
 *   <li>{@link #submit} 只把结果置 {@code submitted} —— 执行者声明完成，AEOS 尚未接受；</li>
 *   <li>{@link #verify} 才置 {@code verified}，且强制两条约束：
 *       <b>独立验证</b>（验证人 ≠ 提交人）与 <b>验证证据</b>（无证据须写明豁免原因）；</li>
 *   <li>{@link #reject} 置 {@code rejected} 并保留原因，供动作方返工后重交。</li>
 * </ul>
 *
 * <p>动作侧的 {@code result_id} 同步回写，使「动作 → 结果」双向可查
 * （穿透样例第 4 段与第 5 段经 {@code action_id} / {@code result_id} 串联）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultService {

    /** 允许提交结果的动作状态：已确认之后才谈得上执行结果。 */
    private static final List<String> SUBMITTABLE_ACTION_STATUS =
            List.of("confirmed", "in_progress", "blocked", "done", "APPROVED");

    private final AgentResultMapper resultMapper;
    private final AgentActionMapper actionMapper;

    /** 结果提交入参（DEH §5：Result 必须挂在 action_id 上）。 */
    public record SubmitCommand(
            String actionId, String objectType, String objectId,
            String expectedResult, String actualResult, String evidenceRefs) {
    }

    /**
     * 提交结果：置 {@code submitted}，不代表已验证。
     *
     * @throws BusinessException action_id 缺失 / 动作不存在 / 动作状态不允许提交 / actual_result 为空
     */
    @Transactional
    public AgentResult submit(SubmitCommand cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.actionId())) {
            throw invalid("action_id 不得为空：结果必须挂在具体动作上（DEH §5）");
        }
        if (!StringUtils.hasText(cmd.actualResult())) {
            throw invalid("actual_result 不得为空：结果必须给出实际产出");
        }
        AgentAction action = findAction(cmd.actionId());
        if (!SUBMITTABLE_ACTION_STATUS.contains(action.getStatus())) {
            throw invalid("动作[" + cmd.actionId() + "]当前状态 " + action.getStatus()
                    + " 不允许提交结果，需先 confirmed");
        }

        AgentResult r = new AgentResult();
        r.setResultId("RES-" + UUID.randomUUID());
        r.setActionId(cmd.actionId());
        r.setObjectType(StringUtils.hasText(cmd.objectType()) ? cmd.objectType() : action.getObjectType());
        r.setObjectId(StringUtils.hasText(cmd.objectId()) ? cmd.objectId() : action.getObjectId());
        r.setExpectedResult(DehJson.value(StringUtils.hasText(cmd.expectedResult())
                ? cmd.expectedResult() : action.getExpectedResult()));
        r.setActualResult(DehJson.value(cmd.actualResult()));
        r.setStatus("submitted");
        r.setSubmittedBy(currentUser());
        r.setSubmittedAt(LocalDateTime.now());
        r.setEvidenceRefs(DehJson.refs(cmd.evidenceRefs()));
        r.setCreatedAt(LocalDateTime.now());
        resultMapper.insert(r);

        // 动作侧回写最新 result_id（动作 → 结果 的反向引用）
        AgentAction upd = new AgentAction();
        upd.setId(action.getId());
        upd.setResultId(r.getResultId());
        actionMapper.updateById(upd);

        log.info("[deh] result submitted {} action={} by={}", r.getResultId(), cmd.actionId(), r.getSubmittedBy());
        return r;
    }

    /**
     * 独立验证：置 {@code verified} = AEOS 接受该结果。
     *
     * @param evidenceRefs 验证证据；为空时必须给出 {@code exemptionReason}，否则拒绝
     * @throws BusinessException 非独立验证 / 状态非 submitted / 无证据且无豁免
     */
    @Transactional
    public AgentResult verify(String resultId, String evidenceRefs, String exemptionReason) {
        AgentResult r = require(resultId);
        if (!"submitted".equals(r.getStatus())) {
            throw denied("结果[" + resultId + "]当前状态 " + r.getStatus() + " 不可验证，仅 submitted 可验证");
        }
        String verifier = currentUser();
        if (verifier.equalsIgnoreCase(nz(r.getSubmittedBy()))) {
            throw denied("非独立验证：验证人 " + verifier + " 与提交人相同。"
                    + "执行者声明完成不等于 AEOS 接受结果，验证必须由他人完成（DEH §8）");
        }
        if (!StringUtils.hasText(evidenceRefs) && !StringUtils.hasText(exemptionReason)) {
            throw denied("验证必须关联证据，或写明明确豁免原因（DEH §8）");
        }

        r.setStatus("verified");
        r.setVerifiedBy(verifier);
        r.setVerifiedAt(LocalDateTime.now());
        r.setVerificationNote(StringUtils.hasText(exemptionReason) ? exemptionReason : null);
        if (StringUtils.hasText(evidenceRefs)) {
            r.setEvidenceRefs(DehJson.refs(evidenceRefs));
        }
        resultMapper.updateById(r);
        log.info("[deh] result verified {} by={} (submitted_by={})", resultId, verifier, r.getSubmittedBy());
        return r;
    }

    /** 驳回：保留原因，动作方返工后可重新提交新结果。 */
    @Transactional
    public AgentResult reject(String resultId, String reason) {
        AgentResult r = require(resultId);
        if (!"submitted".equals(r.getStatus())) {
            throw denied("结果[" + resultId + "]当前状态 " + r.getStatus() + " 不可驳回，仅 submitted 可驳回");
        }
        r.setStatus("rejected");
        r.setVerifiedBy(currentUser());
        r.setVerifiedAt(LocalDateTime.now());
        r.setVerificationNote(reason);
        resultMapper.updateById(r);
        return r;
    }

    public AgentResult get(String resultId) {
        return require(resultId);
    }

    /** 按动作列结果（时间倒序）：一个动作可以有多次提交的历史。 */
    public List<AgentResult> listByAction(String actionId) {
        LambdaQueryWrapper<AgentResult> w = new LambdaQueryWrapper<>();
        w.eq(AgentResult::getActionId, actionId).orderByDesc(AgentResult::getSubmittedAt);
        return resultMapper.selectList(w);
    }

    /** 最新一条结果（穿透链取用）。 */
    public AgentResult latestOfAction(String actionId) {
        List<AgentResult> list = listByAction(actionId);
        return list.isEmpty() ? null : list.get(0);
    }

    private AgentResult require(String resultId) {
        AgentResult r = StringUtils.hasText(resultId) ? resultMapper.selectById(resultId) : null;
        if (r == null) throw new BusinessException(404, "结果不存在: " + resultId);
        return r;
    }

    private AgentAction findAction(String actionId) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        w.eq(AgentAction::getActionId, actionId).last("LIMIT 1");
        List<AgentAction> list = actionMapper.selectList(w);
        if (list.isEmpty()) throw new BusinessException(404, "动作不存在: " + actionId);
        return list.get(0);
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

    private static String nz(String s) { return s == null ? "" : s; }

    private static BusinessException invalid(String msg) {
        return new BusinessException(422, "[" + ApiErrorCodes.DECISION_INVALID + "] " + msg);
    }

    private static BusinessException denied(String msg) {
        return new BusinessException(409, "[" + ApiErrorCodes.RESULT_VERIFY_DENIED + "] " + msg);
    }
}
