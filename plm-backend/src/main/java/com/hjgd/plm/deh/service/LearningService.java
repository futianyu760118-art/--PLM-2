package com.hjgd.plm.deh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.Learning;
import com.hjgd.plm.deh.mapper.LearningMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DEH 学习沉淀（F16 / 架构 V0.2 §3.7）。
 *
 * <p>发布门槛（三项全过才允许 published）：
 * <ol>
 *   <li><b>独立验证</b>：{@code verifiedBy} 非空且 ≠ 产出人；</li>
 *   <li><b>无退化/越权</b>：收益判定不得为 {@code degraded}（比原来更差）
 *       或 {@code overreach}（超出授权范围）；</li>
 *   <li><b>可版本化回退</b>：必须有 {@code rollbackRef} —— 不可回退的经验不得发布。</li>
 * </ol>
 *
 * <p>回退不删除记录：置 {@code rolled_back}，保留 {@code rollbackRef}，
 * 使「这条经验曾经上线过又被撤回」本身成为可审计事实。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningService {

    /** 收益判定取值集。 */
    public static final List<String> BENEFIT_VERDICTS =
            List.of("improved", "neutral", "no_benefit", "degraded", "overreach");

    /** 不予发布的判定（无收益可留档观察，退化与越权必须挡住）。 */
    private static final List<String> BLOCKING_VERDICTS = List.of("degraded", "overreach");

    private final LearningMapper learningMapper;

    /** 学习创建入参（草稿态）。 */
    public record CreateCommand(String source, String content, String version, String rollbackRef) {
    }

    /**
     * 发布入参。
     *
     * @param verifiedBy 验证人回显；只用于核对与登录身份是否一致，验证人以服务端登录身份为准
     * @param benefitVerdict 收益判定，取值见 {@link #BENEFIT_VERDICTS}
     * @param rollbackRef 可版本化回退引用，缺省时取学习条目上已有的值
     */
    public record PublishCommand(String verifiedBy, String benefitVerdict, String rollbackRef) {
    }

    @Transactional
    public Learning create(CreateCommand cmd) {
        if (cmd == null) throw invalid("学习入参为空");
        requireText(cmd.source(), "source");
        requireText(cmd.content(), "content");

        Learning l = new Learning();
        l.setLearningId("LRN-" + UUID.randomUUID());
        l.setSource(cmd.source());
        l.setContent(cmd.content());
        l.setVersion(StringUtils.hasText(cmd.version()) ? cmd.version() : "V1");
        l.setRollbackRef(cmd.rollbackRef());
        l.setStatus("draft");
        l.setCreatedBy(currentUser());
        l.setCreatedAt(LocalDateTime.now());
        learningMapper.insert(l);
        return l;
    }

    /**
     * 发布：三门槛校验，任一不过则拒绝并给出未过项。
     *
     * @throws BusinessException 独立验证缺失 / 判定为退化或越权 / 无回退引用
     */
    @Transactional
    public Learning publish(String learningId, PublishCommand cmd) {
        Learning l = require(learningId);
        if (!"draft".equals(l.getStatus())) {
            throw notPublishable("学习[" + learningId + "]当前状态 " + l.getStatus() + " 不可发布，仅 draft 可发布");
        }
        List<String> blocked = new ArrayList<>();

        // 验证人取自服务端登录身份，不接受客户端自称 —— 否则自评者只要换个字符串就能发布，
        // 「独立验证」这道门槛就形同虚设。入参 verifiedBy 只作回显核对。
        String verifier = currentUser();
        String claimed = cmd == null ? null : cmd.verifiedBy();
        if (StringUtils.hasText(claimed) && !claimed.equalsIgnoreCase(nz(verifier))) {
            blocked.add("独立验证：请求自称验证人 " + claimed + "，实际登录 " + verifier);
        }
        if (verifier.equalsIgnoreCase(nz(l.getCreatedBy()))) {
            blocked.add("独立验证：验证人 " + verifier + " 与产出人相同");
        }

        String verdict = cmd == null ? null : cmd.benefitVerdict();
        if (!StringUtils.hasText(verdict)) {
            blocked.add("收益判定：未提供（取值 " + BENEFIT_VERDICTS + "）");
        } else if (!BENEFIT_VERDICTS.contains(verdict)) {
            blocked.add("收益判定：非法取值 " + verdict);
        } else if (BLOCKING_VERDICTS.contains(verdict)) {
            blocked.add("收益判定：" + verdict + " 不得发布");
        }

        String rollbackRef = cmd != null && StringUtils.hasText(cmd.rollbackRef())
                ? cmd.rollbackRef() : l.getRollbackRef();
        if (!StringUtils.hasText(rollbackRef)) {
            blocked.add("可版本化回退：rollback_ref 缺失");
        }

        if (!blocked.isEmpty()) {
            throw notPublishable("学习[" + learningId + "]不满足发布门槛: " + blocked);
        }

        l.setStatus("published");
        l.setVerifiedBy(verifier);
        l.setVerifiedAt(LocalDateTime.now());
        l.setRollbackRef(rollbackRef);
        l.setPublishedAt(LocalDateTime.now());
        learningMapper.updateById(l);
        log.info("[deh] learning published {} verdict={} by={}", learningId, verdict, verifier);
        return l;
    }

    /** 回退：published → rolled_back，记录保留（撤销本身也是事实）。 */
    @Transactional
    public Learning rollback(String learningId, String reason) {
        Learning l = require(learningId);
        if (!"published".equals(l.getStatus())) {
            throw invalid("学习[" + learningId + "]当前状态 " + l.getStatus() + " 不可回退，仅 published 可回退");
        }
        l.setStatus("rolled_back");
        if (StringUtils.hasText(reason)) {
            l.setContent(l.getContent() + "\n[回退原因] " + reason);
        }
        learningMapper.updateById(l);
        return l;
    }

    public Learning get(String learningId) {
        return require(learningId);
    }

    public List<Learning> listPublished() {
        LambdaQueryWrapper<Learning> w = new LambdaQueryWrapper<>();
        w.eq(Learning::getStatus, "published").orderByDesc(Learning::getPublishedAt);
        return learningMapper.selectList(w);
    }

    private Learning require(String learningId) {
        Learning l = StringUtils.hasText(learningId) ? learningMapper.selectById(learningId) : null;
        if (l == null) throw new BusinessException(404, "学习条目不存在: " + learningId);
        return l;
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

    private static void requireText(String v, String field) {
        if (!StringUtils.hasText(v)) throw invalid(field + " 不得为空");
    }

    private static BusinessException invalid(String msg) {
        return new BusinessException(422, "[" + ApiErrorCodes.DECISION_INVALID + "] " + msg);
    }

    private static BusinessException notPublishable(String msg) {
        return new BusinessException(409, "[" + ApiErrorCodes.LEARNING_NOT_PUBLISHABLE + "] " + msg);
    }
}
