package com.hjgd.plm.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.feedback.entity.Feedback;
import com.hjgd.plm.feedback.mapper.FeedbackMapper;
import com.hjgd.plm.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 问题反馈服务（对齐中台 SPC，含 AI 自动实现）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;

    private static final Set<String> ALLOWED_STATUS = Set.of("pending", "processing", "resolved", "closed");
    private static final Set<String> TERMINAL_STATUS = Set.of("resolved", "closed");
    private static final Set<String> AI_STAGES = Set.of("preparing", "fetching", "agent",
            "gate_backend", "gate_frontend", "committing");
    private static final int PROGRESS_MAX_ENTRIES = 50;

    // ---------- 权限 ----------
    private boolean manager() {
        return SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasPermission("feedback:manage");
    }

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    private String uname() {
        try {
            return SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return "";
        }
    }

    private Feedback entity(Long id) {
        Feedback f = feedbackMapper.selectById(id);
        if (f == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "反馈不存在");
        }
        return f;
    }

    private void requireManager() {
        if (!manager()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权处理反馈");
        }
    }

    // ---------- JSON 工具 ----------
    private String json(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private Object parseJson(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 响应（snake_case，对齐 SPC FeedbackOut / runner） ----------
    private Map<String, Object> toVO(Feedback f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("type", f.getType());
        m.put("severity", f.getSeverity());
        m.put("page_path", f.getPagePath());
        m.put("title", f.getTitle());
        m.put("description", f.getDescription());
        m.put("attachments", parseJson(f.getAttachments()));
        m.put("status", f.getStatus());
        m.put("reply", f.getReply());
        m.put("replied_by", f.getRepliedBy());
        m.put("replied_at", f.getRepliedAt());
        m.put("submitter_id", f.getSubmitterId());
        m.put("submitter_name", f.getSubmitterName());
        m.put("ai_status", f.getAiStatus() == null ? "none" : f.getAiStatus());
        m.put("ai_branch", f.getAiBranch());
        m.put("ai_note", f.getAiNote());
        m.put("ai_requested_by", f.getAiRequestedBy());
        m.put("ai_requested_at", f.getAiRequestedAt());
        m.put("ai_stage", f.getAiStage());
        m.put("ai_stage_started_at", f.getAiStageStartedAt());
        m.put("ai_progress", parseJson(f.getAiProgress()));
        m.put("ai_result", parseJson(f.getAiResult()));
        m.put("created_at", f.getCreatedAt());
        m.put("updated_at", f.getUpdatedAt());
        return m;
    }

    // ---------- 列表 ----------
    @Override
    public Map<String, Object> page(int pageNum, int pageSize, String type, String severity,
                                    String status, String aiStatus, String keyword,
                                    String sortBy, String sortOrder, String submitterName) {
        LambdaQueryWrapper<Feedback> w = new LambdaQueryWrapper<>();
        if (!manager()) {
            w.eq(Feedback::getSubmitterId, uid());
        }
        w.in(StringUtils.hasText(type), Feedback::getType, split(type));
        w.in(StringUtils.hasText(severity), Feedback::getSeverity, split(severity));
        w.in(StringUtils.hasText(status), Feedback::getStatus, split(status));
        w.in(StringUtils.hasText(aiStatus), Feedback::getAiStatus, split(aiStatus));
        w.in(StringUtils.hasText(submitterName), Feedback::getSubmitterName, split(submitterName));
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Feedback::getTitle, keyword)
                    .or().like(Feedback::getPagePath, keyword)
                    .or().like(Feedback::getSubmitterName, keyword)
                    .or().like(Feedback::getRepliedBy, keyword));
        }
        applySort(w, sortBy, sortOrder);
        w.orderByDesc(Feedback::getCreatedAt);

        Page<Feedback> p = feedbackMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Feedback f : p.getRecords()) {
            items.add(toVO(f));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (int) p.getTotal());
        stats.put("pending", countStatus(status, "pending"));
        stats.put("processing", countStatus(status, "processing"));
        stats.put("resolved", countStatus(status, "resolved"));
        stats.put("closed", countStatus(status, "closed"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", (int) p.getTotal());
        out.put("page", pageNum);
        out.put("page_size", pageSize);
        out.put("stats", stats);
        return out;
    }

    private void applySort(LambdaQueryWrapper<Feedback> w, String sortBy, String sortOrder) {
        if (!StringUtils.hasText(sortBy)) return;
        boolean desc = "desc".equalsIgnoreCase(sortOrder);
        switch (sortBy) {
            case "title" -> w.orderBy(true, desc, Feedback::getTitle);
            case "type" -> w.orderBy(true, desc, Feedback::getType);
            case "severity" -> w.orderBy(true, desc, Feedback::getSeverity);
            case "status" -> w.orderBy(true, desc, Feedback::getStatus);
            case "page_path" -> w.orderBy(true, desc, Feedback::getPagePath);
            case "submitter_name" -> w.orderBy(true, desc, Feedback::getSubmitterName);
            case "replied_by" -> w.orderBy(true, desc, Feedback::getRepliedBy);
            case "created_at" -> w.orderBy(true, desc, Feedback::getCreatedAt);
            default -> { }
        }
    }

    private long countStatus(String status, String s) {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getStatus, s)
                .eq(!manager(), Feedback::getSubmitterId, uid()));
    }

    private List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(s)) return out;
        for (String part : s.split(",")) {
            if (StringUtils.hasText(part.trim())) out.add(part.trim());
        }
        return out;
    }

    // ---------- 详情 ----------
    @Override
    public Map<String, Object> get(Long id) {
        Feedback f = entity(id);
        if (!manager() && !f.getSubmitterId().equals(uid())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看");
        }
        return toVO(f);
    }

    // ---------- 创建 ----------
    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Feedback f = new Feedback();
        f.setType(hasText(body.get("type")) ? str(body.get("type")) : "issue");
        f.setSeverity(hasText(body.get("severity")) ? str(body.get("severity")) : "low");
        f.setPagePath(str(body.get("pagePath")));
        String title = str(body.get("title"));
        String desc = str(body.get("description"));
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "标题不能为空");
        }
        if (!StringUtils.hasText(desc)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "描述不能为空");
        }
        f.setTitle(title);
        f.setDescription(desc);
        f.setAttachments(json(body.get("attachments")));
        f.setStatus("pending");
        f.setAiStatus("none");
        f.setSubmitterId(uid());
        f.setSubmitterName(uname());
        feedbackMapper.insert(f);
        return toVO(f);
    }

    // ---------- 提交人更新（仅 pending） ----------
    @Override
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Feedback f = entity(id);
        boolean can = manager();
        if (!can) {
            if (!f.getSubmitterId().equals(uid())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑");
            }
            if (!"pending".equals(f.getStatus())) {
                throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "已处理的反馈不能修改");
            }
        }
        if (body.containsKey("type") && body.get("type") != null) f.setType(str(body.get("type")));
        if (body.containsKey("severity") && body.get("severity") != null) f.setSeverity(str(body.get("severity")));
        if (body.containsKey("pagePath")) f.setPagePath(str(body.get("pagePath")));
        if (body.containsKey("title")) f.setTitle(str(body.get("title")));
        if (body.containsKey("description")) f.setDescription(str(body.get("description")));
        if (body.containsKey("attachments")) f.setAttachments(json(body.get("attachments")));
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    // ---------- 管理员回复 ----------
    @Override
    @Transactional
    public Map<String, Object> reply(Long id, String reply, String status) {
        requireManager();
        Feedback f = entity(id);
        if (!StringUtils.hasText(reply) && !StringUtils.hasText(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回复内容和状态至少填一项");
        }
        if (StringUtils.hasText(reply)) {
            f.setReply(reply);
            f.setRepliedBy(uname());
            f.setRepliedAt(LocalDateTime.now());
        }
        if (StringUtils.hasText(status)) {
            f.setStatus(status);
        } else if (StringUtils.hasText(reply) && "pending".equals(f.getStatus())) {
            f.setStatus("resolved");
        }
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    // ---------- 状态变更 ----------
    @Override
    @Transactional
    public Map<String, Object> changeStatus(Long id, String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法状态");
        }
        Feedback f = entity(id);
        if (manager()) {
            f.setStatus(status);
        } else {
            if (!f.getSubmitterId().equals(uid())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权变更");
            }
            if ("closed".equals(status) && !"resolved".equals(f.getStatus())) {
                throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "只有已解决的反馈才能关闭");
            }
            if ("pending".equals(status) && !"closed".equals(f.getStatus())) {
                throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "只有已关闭的反馈才能重开");
            }
            if (!"closed".equals(status) && !"pending".equals(status)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权变更为该状态");
            }
            f.setStatus(status);
        }
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    // ---------- AI 自动实现 ----------
    @Override
    @Transactional
    public Map<String, Object> aiQueue(Long id) {
        requireManager();
        Feedback f = entity(id);
        if (TERMINAL_STATUS.contains(f.getStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "已解决/已关闭的反馈不能派单");
        }
        if ("queued".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "该反馈已在 AI 排队中");
        }
        if ("done".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "该反馈已由 AI 实现，如需重做请先清理分支");
        }
        f.setAiStatus("queued");
        f.setAiProgress(null);
        f.setAiStage(null);
        f.setAiStageStartedAt(null);
        f.setAiResult(null);
        f.setAiRequestedBy(uname());
        f.setAiRequestedAt(LocalDateTime.now());
        if ("pending".equals(f.getStatus())) {
            f.setStatus("processing");
        }
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    @Override
    @Transactional
    public Map<String, Object> aiCancel(Long id) {
        requireManager();
        Feedback f = entity(id);
        if (!"queued".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "仅排队中的反馈可取消");
        }
        f.setAiStatus("none");
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    @Override
    @Transactional
    public Map<String, Object> aiClaim(Long id) {
        requireManager();
        Feedback f = entity(id);
        if (!"queued".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "反馈当前状态为 " + f.getAiStatus() + "，无法认领");
        }
        f.setAiStatus("running");
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    @Override
    @Transactional
    public Map<String, Object> aiProgress(Long id, String stage, List<String> actions) {
        requireManager();
        Feedback f = entity(id);
        if (!"running".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "反馈当前状态为 " + f.getAiStatus() + "，拒绝上报进度");
        }
        List<String> acts = new ArrayList<>();
        if (actions != null) {
            for (String raw : actions) {
                if (!StringUtils.hasText(raw)) continue;
                acts.add(raw.trim().substring(0, Math.min(200, raw.trim().length())));
            }
        }
        if (!StringUtils.hasText(stage) && acts.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "stage 和 actions 至少填一项");
        }
        if (StringUtils.hasText(stage) && !AI_STAGES.contains(stage)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法阶段: " + stage);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        Object parsed = parseJson(f.getAiProgress());
        if (parsed instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map) entries.add((Map<String, Object>) o);
            }
        }
        String now = LocalDateTime.now().toString().replace("T", " ").substring(0, 19);
        if (StringUtils.hasText(stage)) {
            f.setAiStage(stage);
            f.setAiStageStartedAt(LocalDateTime.now());
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("at", now);
            e.put("kind", "stage");
            e.put("stage", stage);
            e.put("text", stage);
            entries.add(e);
        }
        for (String text : acts) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("at", now);
            e.put("kind", "action");
            e.put("stage", f.getAiStage());
            e.put("text", text);
            entries.add(e);
        }
        if (entries.size() > PROGRESS_MAX_ENTRIES) {
            entries = entries.subList(entries.size() - PROGRESS_MAX_ENTRIES, entries.size());
        }
        f.setAiProgress(json(entries));
        feedbackMapper.updateById(f);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ai_status", f.getAiStatus());
        out.put("ai_stage", f.getAiStage());
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> aiFinish(Long id, boolean success, String branch, String summary,
                                        String error, Map<String, Object> result) {
        requireManager();
        Feedback f = entity(id);
        if (!"running".equals(f.getAiStatus())) {
            throw new BusinessException(ResultCode.FEEDBACK_STATUS_ERROR, "反馈当前状态为 " + f.getAiStatus() + "，拒绝回写");
        }
        if (success) {
            f.setAiStatus("done");
            f.setAiBranch(branch);
            f.setAiNote(summary);
            f.setReply("【AI 自动实现待审核】分支：" + (branch == null ? "—" : branch)
                    + "。\n验证说明：" + (StringUtils.hasText(summary) ? summary : "已通过自动门禁")
                    + "。\n请在开发机审核 diff 并合入后再更新本反馈状态。");
        } else {
            f.setAiStatus("failed");
            f.setAiNote(error);
            f.setReply("【AI 自动实现失败】" + (StringUtils.hasText(error) ? error : "未知原因") + "。修改反馈后可点击「重试」。");
        }
        f.setAiResult(json(result));
        f.setAiStage(null);
        f.setAiStageStartedAt(null);
        f.setRepliedBy("AI Runner");
        f.setRepliedAt(LocalDateTime.now());
        feedbackMapper.updateById(f);
        return toVO(f);
    }

    @Override
    public List<Long> aiQueueWait(int timeoutSeconds) {
        requireManager();
        int timeout = Math.max(1, Math.min(30, timeoutSeconds));
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        List<Long> ids = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            List<Feedback> queued = feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getAiStatus, "queued")
                    .orderByAsc(Feedback::getCreatedAt)
                    .last("LIMIT 10"));
            if (!queued.isEmpty()) {
                for (Feedback f : queued) ids.add(f.getId());
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return ids;
    }

    // ---------- 删除 ----------
    @Override
    @Transactional
    public void delete(Long id) {
        Feedback f = entity(id);
        if (!manager()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除反馈");
        }
        feedbackMapper.deleteById(f.getId());
    }

    // ---------- 工具 ----------
    private boolean hasText(Object o) {
        return o != null && StringUtils.hasText(String.valueOf(o));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
