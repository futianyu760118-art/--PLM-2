package com.hjgd.plm.feedback.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 问题反馈（对齐中台 SPC，含 AI 自动实现）。
 * 权限: 任意登录用户可提交/查看本人；处理/派单/删除需 feedback:manage 或 ADMIN。
 */
@Tag(name = "问题反馈")
@RestController
@RequestMapping({"/v1/feedback", "/feedback"})
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表(筛选+统计)")
    @GetMapping("")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String severity,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String aiStatus,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String sortBy,
                                            @RequestParam(required = false) String sortOrder,
                                            @RequestParam(required = false) String submitterName) {
        return Result.success(feedbackService.page(page, pageSize, type, severity, status,
                aiStatus, keyword, sortBy, sortOrder, submitterName));
    }

    @Operation(summary = "AI 队列长轮询(处理人)")
    @GetMapping("/ai-queue-wait")
    public Result<Map<String, Object>> aiQueueWait(@RequestParam(defaultValue = "20") int timeoutSeconds) {
        return Result.success(Map.of("ids", feedbackService.aiQueueWait(timeoutSeconds)));
    }

    @Operation(summary = "反馈详情(本人或处理人)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.success(feedbackService.get(id));
    }

    @Operation(summary = "提交反馈")
    @PostMapping("")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.success(feedbackService.create(body));
    }

    @Operation(summary = "提交人编辑(仅 pending)")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(feedbackService.update(id, body));
    }

    @Operation(summary = "管理员回复(可同步状态)")
    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reply = body.get("reply") == null ? null : String.valueOf(body.get("reply"));
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        return Result.success(feedbackService.reply(id, reply, status));
    }

    @Operation(summary = "状态变更")
    @PatchMapping("/{id}/status")
    public Result<Map<String, Object>> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(feedbackService.changeStatus(id, String.valueOf(body.get("status"))));
    }

    @Operation(summary = "AI 派单")
    @PostMapping("/{id}/ai-queue")
    public Result<Map<String, Object>> aiQueue(@PathVariable Long id) {
        return Result.success(feedbackService.aiQueue(id));
    }

    @Operation(summary = "AI 取消排队")
    @PostMapping("/{id}/ai-cancel")
    public Result<Map<String, Object>> aiCancel(@PathVariable Long id) {
        return Result.success(feedbackService.aiCancel(id));
    }

    @Operation(summary = "AI Runner 认领")
    @PostMapping("/{id}/ai-claim")
    public Result<Map<String, Object>> aiClaim(@PathVariable Long id) {
        return Result.success(feedbackService.aiClaim(id));
    }

    @Operation(summary = "AI Runner 上报进度")
    @PostMapping("/{id}/ai-progress")
    public Result<Map<String, Object>> aiProgress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String stage = body.get("stage") == null ? null : String.valueOf(body.get("stage"));
        List<String> actions = null;
        if (body.get("actions") instanceof List<?> list) {
            actions = list.stream().map(String::valueOf).toList();
        }
        return Result.success(feedbackService.aiProgress(id, stage, actions));
    }

    @Operation(summary = "AI Runner 回写结果")
    @PostMapping("/{id}/ai-finish")
    public Result<Map<String, Object>> aiFinish(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean success = Boolean.TRUE.equals(body.get("success"));
        String branch = body.get("branch") == null ? null : String.valueOf(body.get("branch"));
        String summary = body.get("summary") == null ? null : String.valueOf(body.get("summary"));
        String error = body.get("error") == null ? null : String.valueOf(body.get("error"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = body.get("result") instanceof Map ? (Map<String, Object>) body.get("result") : null;
        return Result.success(feedbackService.aiFinish(id, success, branch, summary, error, result));
    }

    @Operation(summary = "删除反馈(处理人)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        feedbackService.delete(id);
        return Result.success();
    }
}
