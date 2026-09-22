package com.hjgd.plm.improve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.improve.entity.ImproveAction;
import com.hjgd.plm.improve.entity.ImproveResult;
import com.hjgd.plm.improve.entity.Insight;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.entity.StandardWork;
import com.hjgd.plm.improve.mapper.ImproveActionMapper;
import com.hjgd.plm.improve.mapper.ImproveResultMapper;
import com.hjgd.plm.improve.mapper.InsightMapper;
import com.hjgd.plm.improve.mapper.IssueMapper;
import com.hjgd.plm.improve.mapper.StandardWorkMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImproveService {

    private final IssueMapper issueMapper;
    private final ImproveActionMapper actionMapper;
    private final InsightMapper insightMapper;
    private final ImproveResultMapper resultMapper;
    private final StandardWorkMapper standardWorkMapper;
    private final SequenceService sequenceService;

    public List<Issue> listIssues(String status, String severity) {
        LambdaQueryWrapper<Issue> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(status), Issue::getStatus, status)
                .eq(StringUtils.hasText(severity), Issue::getSeverity, severity)
                .orderByDesc(Issue::getCreatedAt);
        return issueMapper.selectList(w);
    }

    public Issue getIssue(Long id) {
        return issueMapper.selectById(id);
    }

    @Transactional
    public Issue createIssue(Issue issue) {
        issue.setIssueNo(sequenceService.nextNo("ISSUE_NO"));
        if (!StringUtils.hasText(issue.getStatus())) {
            issue.setStatus("OPEN");
        }
        if (!StringUtils.hasText(issue.getSourceType())) {
            issue.setSourceType("USER");
        }
        issue.setCreatedAt(LocalDateTime.now());
        issueMapper.insert(issue);
        return issue;
    }

    @Transactional
    public Insight createInsight(Insight insight) {
        insight.setInsightNo(sequenceService.nextNo("INSIGHT_NO"));
        if (!StringUtils.hasText(insight.getStatus())) {
            insight.setStatus("NEW");
        }
        if (!StringUtils.hasText(insight.getSource())) {
            insight.setSource("SYSTEM");
        }
        insight.setCreatedAt(LocalDateTime.now());
        insightMapper.insert(insight);
        return insight;
    }

    /**
     * 改进问题合法状态流转 (守卫): 非法跳转一律拒绝, 不再任意改状态。
     * 与 v5 §4.1「转换 = 守卫 + 动作 + 事件」一致; 终态 CLOSED/CANCEL 不可再转。
     */
    private static final Map<String, Set<String>> ISSUE_TRANSITIONS = Map.of(
            "OPEN", Set.of("ANALYZING", "CANCEL"),
            "ANALYZING", Set.of("ACTION", "CANCEL"),
            "ACTION", Set.of("VERIFY", "CANCEL"),
            "VERIFY", Set.of("CLOSED", "ACTION"),
            "CLOSED", Set.of(),
            "CANCEL", Set.of());

    @Transactional
    public void updateIssueStatus(Long id, String status) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        String from = issue.getStatus() == null ? "" : issue.getStatus();
        if (!ISSUE_TRANSITIONS.getOrDefault(from, Set.of()).contains(status)) {
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 问题状态不允许: "
                    + from + " --" + status + "--> ?");
        }
        issue.setStatus(status);
        if ("CLOSED".equals(status) || "CANCEL".equals(status)) {
            issue.setClosedAt(LocalDateTime.now());
        }
        issueMapper.updateById(issue);
    }

    public List<ImproveAction> listActions(Long issueId) {
        return actionMapper.selectList(
                new LambdaQueryWrapper<ImproveAction>().eq(ImproveAction::getIssueId, issueId));
    }

    @Transactional
    public ImproveAction createAction(ImproveAction action) {
        action.setActionNo(sequenceService.nextNo("ISSUE_NO"));
        if (!StringUtils.hasText(action.getStatus())) {
            action.setStatus("OPEN");
        }
        action.setCreatedAt(LocalDateTime.now());
        actionMapper.insert(action);
        return action;
    }

    @Transactional
    public void completeAction(Long actionId) {
        ImproveAction action = actionMapper.selectById(actionId);
        if (action != null) {
            action.setStatus("DONE");
            action.setDoneAt(LocalDateTime.now());
            actionMapper.updateById(action);
        }
    }

    public List<Insight> listInsights(String status) {
        LambdaQueryWrapper<Insight> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(status), Insight::getStatus, status)
                .orderByDesc(Insight::getCreatedAt);
        return insightMapper.selectList(w);
    }

    @Transactional
    public Insight convertInsightToIssue(Long insightId) {
        Insight insight = insightMapper.selectById(insightId);
        if (insight == null) {
            return null;
        }
        Issue issue = new Issue();
        issue.setSourceType("INSIGHT");
        issue.setSourceRef(insight.getInsightNo());
        issue.setTitle(insight.getTitle());
        issue.setSeverity(insight.getSeverity());
        issue.setCategory(insight.getCategory());
        issue.setKpiCodes(insight.getRelatedKpiCodes());
        issue.setStatus("OPEN");
        issue = createIssue(issue);

        insight.setStatus("CONVERTED_ISSUE");
        insightMapper.updateById(insight);
        return insight;
    }

    @Transactional
    public void dismissInsight(Long insightId) {
        Insight insight = insightMapper.selectById(insightId);
        if (insight != null) {
            insight.setStatus("DISMISSED");
            insightMapper.updateById(insight);
        }
    }

    // ==================== 改善成效回写 ====================

    public List<ImproveResult> listResults(Long issueId) {
        return resultMapper.selectList(
                new LambdaQueryWrapper<ImproveResult>().eq(ImproveResult::getIssueId, issueId));
    }

    /** 记录某 issue 对某 KPI 的前后差值(改善前/改善后窗口均值) */
    @Transactional
    public ImproveResult recordResult(ImproveResult result) {
        if (result.getWindowFrom() == null) {
            result.setWindowFrom(LocalDate.now().minusDays(30));
        }
        if (result.getWindowTo() == null) {
            result.setWindowTo(LocalDate.now());
        }
        result.setEffective(null);
        resultMapper.insert(result);
        return result;
    }

    /** 验证改善成效：标记是否真有效 */
    @Transactional
    public ImproveResult verifyResult(Long resultId, boolean effective) {
        ImproveResult r = resultMapper.selectById(resultId);
        if (r == null) {
            return null;
        }
        r.setEffective(effective);
        r.setVerifiedAt(LocalDateTime.now());
        try {
            r.setVerifiedBy(SecurityUtils.getCurrentUserId());
        } catch (Exception ignored) {
        }
        resultMapper.updateById(r);
        return r;
    }

    // ==================== 标准作业库(改善固化) ====================

    public List<StandardWork> listStandardWorks(String status) {
        LambdaQueryWrapper<StandardWork> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(status), StandardWork::getStatus, status)
                .orderByDesc(StandardWork::getCreatedAt);
        return standardWorkMapper.selectList(w);
    }

    public StandardWork getStandardWork(Long id) {
        return standardWorkMapper.selectById(id);
    }

    /** 改善验证有效后固化为标准作业(从 issue 派生) */
    @Transactional
    public StandardWork createStandardWork(StandardWork sw) {
        sw.setSwNo(sequenceService.nextNo("ISSUE_NO"));
        if (!StringUtils.hasText(sw.getStatus())) {
            sw.setStatus("DRAFT");
        }
        if (!StringUtils.hasText(sw.getVersionNo())) {
            sw.setVersionNo("V1");
        }
        sw.setCreatedAt(LocalDateTime.now());
        standardWorkMapper.insert(sw);
        return sw;
    }

    @Transactional
    public StandardWork publishStandardWork(Long swId) {
        StandardWork sw = standardWorkMapper.selectById(swId);
        if (sw != null) {
            sw.setStatus("PUBLISHED");
            sw.setPublishedAt(LocalDateTime.now());
            standardWorkMapper.updateById(sw);
        }
        return sw;
    }
}
