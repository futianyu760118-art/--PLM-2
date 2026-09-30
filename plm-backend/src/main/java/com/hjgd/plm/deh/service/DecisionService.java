package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DEH 决策（F16 / DEH_CONTRACT_V1 §3）。
 *
 * <p>决策是穿透链的第 2 段：它由上游「管理结果」触发
 * （{@code source_type/source_id} 指回触发它的指标/事件/问题），
 * 又被下游动作承接（动作的 {@code decision_id} 指回本决策）。
 *
 * <p>已确认的决策不原地改写：口径或结论变化时置 {@code superseded} 并新建一条，
 * 保证「当时为什么这么定」可回溯 —— 这也是决策证据能成立的前提。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionService {

    /**
     * DEH §3 source_type 取值集 —— 触发来源的「种类」，不是业务对象类型。
     *
     * <p>注意指标这类业务对象不在此列：它挂 {@code object_type/object_id}（object_ref）。
     * 例如「CF 口径失真」这条决策的 source 是 {@code system}（指标系统报出），
     * 而 object_ref 才是 {@code (metric, METRIC-001)}。别把 metric 塞进 source_type。
     */
    public static final List<String> SOURCE_TYPES =
            List.of("meeting", "event", "issue", "agent", "human", "system");

    private final PlmDecisionMapper decisionMapper;

    /** 决策创建入参。 */
    public record CreateCommand(
            String sourceType, String sourceId, String objectType, String objectId,
            String content, String reason, String decisionOwnerId,
            String decisionCaseId, String goalId, String evidenceRefs) {
    }

    /**
     * 建立决策（draft）。创建时不校验源对象存在性 —— 决策允许先于源对象落库，
     * 但 {@code source_type/source_id} 必须齐备，否则穿透链第 1→2 段无从关联。
     */
    @Transactional
    public PlmDecision create(CreateCommand cmd) {
        if (cmd == null) throw invalid("决策入参为空");
        requireText(cmd.sourceType(), "source_type");
        if (!SOURCE_TYPES.contains(cmd.sourceType())) {
            throw invalid("非法 source_type: " + cmd.sourceType() + "，允许值 " + SOURCE_TYPES);
        }
        requireText(cmd.sourceId(), "source_id");
        requireText(cmd.content(), "content");
        requireText(cmd.decisionOwnerId(), "decision_owner_id");

        PlmDecision d = new PlmDecision();
        d.setDecisionId("DEC-" + UUID.randomUUID());
        d.setDecisionCaseId(cmd.decisionCaseId());
        d.setGoalId(cmd.goalId());
        d.setSourceType(cmd.sourceType());
        d.setSourceId(cmd.sourceId());
        d.setObjectType(cmd.objectType());
        d.setObjectId(cmd.objectId());
        d.setContent(cmd.content());
        d.setReason(cmd.reason());
        d.setDecisionOwnerId(cmd.decisionOwnerId());
        d.setStatus("draft");
        d.setEvidenceRefs(DehJson.refs(cmd.evidenceRefs()));
        d.setCreatedBy(currentUser());
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        decisionMapper.insert(d);
        log.info("[deh] decision created {} source={}/{}", d.getDecisionId(), cmd.sourceType(), cmd.sourceId());
        return d;
    }

    /** 确认决策：draft → confirmed，记录决策时点（未确认的决策不构成可执行依据）。 */
    @Transactional
    public PlmDecision confirm(String decisionId) {
        PlmDecision d = require(decisionId);
        if (!"draft".equals(d.getStatus())) {
            throw invalid("决策[" + decisionId + "]当前状态 " + d.getStatus() + " 不可确认，仅 draft 可确认");
        }
        d.setStatus("confirmed");
        d.setDecidedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        decisionMapper.updateById(d);
        return d;
    }

    /** 取代决策：原决策保留并置 superseded，结论变更须新建决策（不改写历史）。 */
    @Transactional
    public PlmDecision supersede(String decisionId, String reason) {
        PlmDecision d = require(decisionId);
        if ("superseded".equals(d.getStatus())) {
            throw invalid("决策[" + decisionId + "]已是 superseded");
        }
        d.setStatus("superseded");
        if (StringUtils.hasText(reason)) {
            d.setReason(StringUtils.hasText(d.getReason()) ? d.getReason() + " | 取代原因: " + reason : reason);
        }
        d.setUpdatedAt(LocalDateTime.now());
        decisionMapper.updateById(d);
        return d;
    }

    /** 取消决策（未执行的误立决策；已确认并已产生动作的应走 supersede 而非 cancel）。 */
    @Transactional
    public PlmDecision cancel(String decisionId, String reason) {
        PlmDecision d = require(decisionId);
        if ("confirmed".equals(d.getStatus())) {
            throw invalid("已确认的决策[" + decisionId + "]不可直接取消，请用 supersede 保留历史");
        }
        d.setStatus("cancelled");
        if (StringUtils.hasText(reason)) d.setReason(reason);
        d.setUpdatedAt(LocalDateTime.now());
        decisionMapper.updateById(d);
        return d;
    }

    public PlmDecision get(String decisionId) {
        return require(decisionId);
    }

    /** 按上游来源查决策（穿透链第 1→2 段：指标 → 决策）。 */
    public List<PlmDecision> listBySource(String sourceType, String sourceId) {
        LambdaQueryWrapper<PlmDecision> w = new LambdaQueryWrapper<>();
        w.eq(PlmDecision::getSourceType, sourceType)
                .eq(PlmDecision::getSourceId, sourceId)
                .orderByAsc(PlmDecision::getCreatedAt);
        return decisionMapper.selectList(w);
    }

    private PlmDecision require(String decisionId) {
        PlmDecision d = StringUtils.hasText(decisionId) ? decisionMapper.selectById(decisionId) : null;
        if (d == null) throw new BusinessException(404, "决策不存在: " + decisionId);
        return d;
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
