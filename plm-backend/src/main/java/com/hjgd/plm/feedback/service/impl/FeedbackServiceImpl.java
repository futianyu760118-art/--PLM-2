package com.hjgd.plm.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.feedback.FeedbackConstants;
import com.hjgd.plm.feedback.dto.FeedbackQueryDTO;
import com.hjgd.plm.feedback.dto.FeedbackReceiptVO;
import com.hjgd.plm.feedback.dto.FeedbackSubmitDTO;
import com.hjgd.plm.feedback.dto.FeedbackTransitionDTO;
import com.hjgd.plm.feedback.dto.FeedbackVO;
import com.hjgd.plm.feedback.entity.Feedback;
import com.hjgd.plm.feedback.mapper.FeedbackMapper;
import com.hjgd.plm.feedback.service.FeedbackService;
import com.hjgd.plm.system.entity.SysUser;
import com.hjgd.plm.system.mapper.SysUserMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final String PERM_MANAGE = "feedback:manage";
    private static final String SEQ_KEY = "FEEDBACK_NO";
    private static final String DEDUP_KEY_PREFIX = "fb:dedup:";

    private static final Map<String, String> STATUS_LABELS = Map.of(
            FeedbackConstants.STATUS_PENDING, "待分析",
            FeedbackConstants.STATUS_EVALUATING, "评估中",
            FeedbackConstants.STATUS_INITIATED, "已立项",
            FeedbackConstants.STATUS_RETURNED, "已退回");

    private final FeedbackMapper feedbackMapper;
    private final SysUserMapper sysUserMapper;
    private final SequenceService sequenceService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackReceiptVO submit(FeedbackSubmitDTO dto) {
        String type = trim(dto.getType());
        String content = trim(dto.getContent());
        String expectedResult = trim(dto.getExpectedResult());
        String pagePath = trim(dto.getPagePath());
        String contact = trim(dto.getContact());

        validate(type, content, expectedResult, pagePath, contact);

        Long submitterId = SecurityUtils.getCurrentUserId();
        String pageHash = md5(pagePath);
        String contentHash = md5(content);
        String dedupKey = DEDUP_KEY_PREFIX + submitterId + ":" + type + ":" + pageHash + ":" + contentHash;

        if (isDedupLocked(dedupKey) || existsRecentDuplicate(submitterId, type, pageHash, contentHash)) {
            throw new BusinessException(ResultCode.FEEDBACK_DUPLICATE);
        }

        Feedback entity = new Feedback();
        entity.setType(type);
        entity.setContent(content);
        entity.setExpectedResult(expectedResult);
        entity.setPagePath(pagePath);
        entity.setPageHash(pageHash);
        entity.setContact(contact);
        entity.setContentHash(contentHash);
        entity.setStatus(FeedbackConstants.STATUS_PENDING);
        entity.setSubmitterId(submitterId);
        entity.setFeedbackNo(sequenceService.nextNo(SEQ_KEY));
        try {
            feedbackMapper.insert(entity);
        } catch (RuntimeException e) {
            // 落库失败不占用去重窗口,允许用户原样重试
            releaseDedupLock(dedupKey);
            throw e;
        }
        return new FeedbackReceiptVO(entity.getFeedbackNo(), entity.getCreatedAt());
    }

    @Override
    public FeedbackVO getByNo(String feedbackNo) {
        Feedback fb = feedbackMapper.selectOne(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getFeedbackNo, feedbackNo));
        boolean manage = SecurityUtils.hasPermission(PERM_MANAGE);
        if (fb == null || (!manage && !Objects.equals(fb.getSubmitterId(), SecurityUtils.getCurrentUserId()))) {
            // 非本人且非总控时按不存在处理,避免泄露他人反馈是否存在
            throw new BusinessException(ResultCode.NOT_FOUND, "反馈编号不存在");
        }
        return toVO(fb, manage);
    }

    @Override
    public PageResult<FeedbackVO> page(FeedbackQueryDTO query) {
        LambdaQueryWrapper<Feedback> w = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(query.getStatus()), Feedback::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getType()), Feedback::getType, query.getType())
                .orderByDesc(Feedback::getCreatedAt);
        applyDateRange(w, query.getDateFrom(), query.getDateTo());
        Page<Feedback> page = feedbackMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), w);

        List<Feedback> records = page.getRecords();
        List<Long> userIds = new ArrayList<>();
        records.forEach(f -> {
            userIds.add(f.getSubmitterId());
            userIds.add(f.getEvaluatedBy());
        });
        Map<Long, String> names = realNames(userIds);

        List<FeedbackVO> vos = records.stream().map(f -> {
            FeedbackVO vo = toVO(f, true);
            vo.setSubmitterName(names.get(f.getSubmitterId()));
            vo.setEvaluatedByName(names.get(f.getEvaluatedBy()));
            return vo;
        }).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long id, FeedbackTransitionDTO dto) {
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "反馈不存在");
        }
        String target = trim(dto.getTargetStatus());
        List<String> allowed = FeedbackConstants.TRANSITIONS.getOrDefault(fb.getStatus(), List.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR,
                    "当前状态「" + label(fb.getStatus()) + "」不允许流转为「" + label(target) + "」");
        }
        String note = trim(dto.getNote());
        if (note.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "流转必须填写分析结论或退回原因");
        }
        String relatedIssueNo = trim(dto.getRelatedIssueNo());
        if (FeedbackConstants.STATUS_INITIATED.equals(target) && relatedIssueNo.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "立项流转必须回填立项 issue 编号");
        }

        fb.setStatus(target);
        fb.setEvaluateNote(note);
        if (FeedbackConstants.STATUS_INITIATED.equals(target)) {
            fb.setRelatedIssueNo(relatedIssueNo);
        }
        fb.setEvaluatedBy(SecurityUtils.getCurrentUserId());
        fb.setEvaluatedAt(LocalDateTime.now());
        feedbackMapper.updateById(fb);
    }

    /** AC F8:一次性收集全部字段错误后统一抛出,避免用户逐个试错 */
    private void validate(String type, String content, String expectedResult, String pagePath, String contact) {
        List<String> errors = new ArrayList<>();
        if (!FeedbackConstants.TYPES.contains(type)) {
            errors.add("反馈类型必须为 缺陷/建议/需求 之一");
        }
        if (content.isEmpty()) {
            errors.add("反馈内容不能为空");
        } else if (content.length() < FeedbackConstants.CONTENT_MIN || content.length() > FeedbackConstants.CONTENT_MAX) {
            errors.add("反馈内容长度需为 " + FeedbackConstants.CONTENT_MIN + "-" + FeedbackConstants.CONTENT_MAX + " 字符");
        }
        if (expectedResult.length() > FeedbackConstants.EXPECTED_MAX) {
            errors.add("期望结果长度不能超过 " + FeedbackConstants.EXPECTED_MAX + " 字符");
        }
        if (FeedbackConstants.TYPE_REQUIREMENT.equals(type) && expectedResult.isEmpty()) {
            errors.add("反馈类型为「需求」时期望结果必填");
        }
        if (pagePath.isEmpty()) {
            errors.add("页面路径不能为空");
        } else if (pagePath.length() > FeedbackConstants.PAGE_PATH_MAX) {
            errors.add("页面路径长度不能超过 " + FeedbackConstants.PAGE_PATH_MAX + " 字符");
        }
        if (contact.length() > FeedbackConstants.CONTACT_MAX) {
            errors.add("联系方式长度不能超过 " + FeedbackConstants.CONTACT_MAX + " 字符");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, String.join("; ", errors));
        }
    }

    private boolean isDedupLocked(String dedupKey) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofSeconds(FeedbackConstants.DEDUP_WINDOW_SECONDS));
            return Boolean.FALSE.equals(acquired);
        } catch (Exception e) {
            log.warn("反馈去重缓存不可用,降级为数据库判定: {}", e.getMessage());
            return false;
        }
    }

    private void releaseDedupLock(String dedupKey) {
        try {
            stringRedisTemplate.delete(dedupKey);
        } catch (Exception e) {
            log.warn("反馈去重缓存释放失败: {}", e.getMessage());
        }
    }

    /** AC F11:同一提交人 + 同类型 + 同页面 + 同内容,5 分钟窗口内视为重复 */
    private boolean existsRecentDuplicate(Long submitterId, String type, String pageHash, String contentHash) {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getSubmitterId, submitterId)
                .eq(Feedback::getType, type)
                .eq(Feedback::getPageHash, pageHash)
                .eq(Feedback::getContentHash, contentHash)
                .gt(Feedback::getCreatedAt,
                        LocalDateTime.now().minusSeconds(FeedbackConstants.DEDUP_WINDOW_SECONDS))) > 0;
    }

    private FeedbackVO toVO(Feedback fb, boolean includeContact) {
        FeedbackVO vo = new FeedbackVO();
        BeanUtils.copyProperties(fb, vo);
        if (!includeContact) {
            vo.setContact(null);
        }
        return vo;
    }

    private Map<Long, String> realNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
            names.put(u.getId(), u.getRealName() == null ? "" : u.getRealName());
        }
        return names;
    }

    private static String label(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    /**
     * 时间范围筛选(AC F16b): 起止同日时包含当日全部记录; 结束早于开始则拒绝并提示。
     * 注意: 条件包装器是即时求值的,取值必须先判空再计算,否则 dateFrom 为空时会 NPE。
     */
    private void applyDateRange(LambdaQueryWrapper<Feedback> w, String dateFrom, String dateTo) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间不能早于开始时间");
        }
        if (from != null) {
            w.ge(Feedback::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            w.le(Feedback::getCreatedAt, to.atTime(LocalTime.MAX));
        }
    }

    private static LocalDate parseDate(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "时间格式需为 yyyy-MM-dd");
        }
    }

    private static String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
