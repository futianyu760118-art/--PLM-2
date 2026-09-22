package com.hjgd.plm.improve.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.improve.entity.ImproveAction;
import com.hjgd.plm.improve.entity.ImproveResult;
import com.hjgd.plm.improve.entity.Insight;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.entity.StandardWork;
import com.hjgd.plm.improve.service.ImproveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "V1 问题改善闭环")
@RestController
@RequestMapping("/v1/issues")
@RequiredArgsConstructor
public class ImproveController {

    private final ImproveService improveService;

    @Operation(summary = "问题列表")
    @GetMapping
    public Result<List<Issue>> listIssues(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String severity) {
        return Result.success(improveService.listIssues(status, severity));
    }

    @Operation(summary = "问题详情")
    @GetMapping("/{id}")
    public Result<Issue> get(@PathVariable Long id) {
        return Result.success(improveService.getIssue(id));
    }

    @Operation(summary = "创建问题")
    @PostMapping
    public Result<Issue> create(@RequestBody Issue issue) {
        return Result.success(improveService.createIssue(issue));
    }

    @Operation(summary = "更新状态")
    @PostMapping("/{id}/actions/update-status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        improveService.updateIssueStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "问题对策列表")
    @GetMapping("/{issueId}/actions")
    public Result<List<ImproveAction>> listActions(@PathVariable Long issueId) {
        return Result.success(improveService.listActions(issueId));
    }

    @Operation(summary = "添加对策")
    @PostMapping("/{issueId}/actions")
    public Result<ImproveAction> createAction(@PathVariable Long issueId, @RequestBody ImproveAction action) {
        action.setIssueId(issueId);
        return Result.success(improveService.createAction(action));
    }

    @Operation(summary = "完成对策")
    @PostMapping("/actions/{actionId}/complete")
    public Result<Void> completeAction(@PathVariable Long actionId) {
        improveService.completeAction(actionId);
        return Result.success();
    }

    @Operation(summary = "洞察列表")
    @GetMapping("/insights")
    public Result<List<Insight>> insights(@RequestParam(required = false) String status) {
        return Result.success(improveService.listInsights(status));
    }

    @Operation(summary = "洞察转问题")
    @PostMapping("/insights/{id}/convert")
    public Result<Insight> convert(@PathVariable Long id) {
        return Result.success(improveService.convertInsightToIssue(id));
    }

    @Operation(summary = "忽略洞察")
    @PostMapping("/insights/{id}/dismiss")
    public Result<Void> dismiss(@PathVariable Long id) {
        improveService.dismissInsight(id);
        return Result.success();
    }

    // ==================== 改善成效回写 ====================

    @Operation(summary = "改善成效列表(某问题对 KPI 前后差值)")
    @GetMapping("/{issueId}/results")
    public Result<List<ImproveResult>> listResults(@PathVariable Long issueId) {
        return Result.success(improveService.listResults(issueId));
    }

    @Operation(summary = "记录改善成效(before/after)")
    @PostMapping("/{issueId}/results")
    public Result<ImproveResult> recordResult(@PathVariable Long issueId, @RequestBody ImproveResult result) {
        result.setIssueId(issueId);
        return Result.success(improveService.recordResult(result));
    }

    @Operation(summary = "验证改善成效是否有效")
    @PostMapping("/results/{resultId}/verify")
    public Result<ImproveResult> verifyResult(@PathVariable Long resultId, @RequestParam boolean effective) {
        return Result.success(improveService.verifyResult(resultId, effective));
    }

    // ==================== 标准作业库(改善固化) ====================

    @Operation(summary = "标准作业列表")
    @GetMapping("/standard-works")
    public Result<List<StandardWork>> listStandardWorks(@RequestParam(required = false) String status) {
        return Result.success(improveService.listStandardWorks(status));
    }

    @Operation(summary = "固化为标准作业(改善验证有效后)")
    @PostMapping("/standard-works")
    public Result<StandardWork> createStandardWork(@RequestBody StandardWork sw) {
        return Result.success(improveService.createStandardWork(sw));
    }

    @Operation(summary = "发布标准作业")
    @PostMapping("/standard-works/{swId}/publish")
    public Result<StandardWork> publishStandardWork(@PathVariable Long swId) {
        return Result.success(improveService.publishStandardWork(swId));
    }
}
