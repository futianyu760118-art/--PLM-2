package com.hjgd.plm.m04.approval.controller;

import com.hjgd.plm.m04.approval.dto.ApprovalActionDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalFlowDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.dto.PendingTaskVO;
import com.hjgd.plm.m04.approval.entity.ApprovalFlow;
import com.hjgd.plm.m04.approval.entity.ApprovalInstance;
import com.hjgd.plm.m04.approval.entity.ApprovalNode;
import com.hjgd.plm.m04.approval.entity.ApprovalTask;
import com.hjgd.plm.m04.approval.service.ApprovalService;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "审批流引擎")
@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    // ---------- 流程定义 ----------

    @Operation(summary = "流程列表")
    @GetMapping("/flows")
    public Result<List<ApprovalFlow>> flows(@RequestParam(required = false) String objectType) {
        return Result.success(approvalService.listFlows(objectType));
    }

    @Operation(summary = "流程节点")
    @GetMapping("/flows/{flowId}/nodes")
    public Result<List<ApprovalNode>> nodes(@PathVariable Long flowId) {
        return Result.success(approvalService.listNodes(flowId));
    }

    @Operation(summary = "保存流程（含节点）")
    @PostMapping("/flows")
    public Result<ApprovalFlow> saveFlow(@Valid @RequestBody ApprovalFlowDTO dto) {
        return Result.success(approvalService.saveFlow(dto));
    }

    @Operation(summary = "启用/停用流程")
    @PutMapping("/flows/{flowId}/status")
    public Result<Void> changeStatus(@PathVariable Long flowId, @RequestParam String status) {
        approvalService.changeFlowStatus(flowId, status);
        return Result.success();
    }

    @Operation(summary = "删除流程")
    @DeleteMapping("/flows/{flowId}")
    public Result<Void> deleteFlow(@PathVariable Long flowId) {
        approvalService.deleteFlow(flowId);
        return Result.success();
    }

    // ---------- 审批操作 ----------

    @Operation(summary = "待办箱")
    @GetMapping("/pending")
    public Result<List<PendingTaskVO>> pending() {
        return Result.success(approvalService.pendingOfCurrentUser());
    }

    @Operation(summary = "审批动作（通过/驳回/退回/终止）")
    @PostMapping("/{instanceId}/act")
    public Result<ApprovalInstance> act(@PathVariable Long instanceId,
                                        @Valid @RequestBody ApprovalActionDTO dto) {
        return Result.success(approvalService.act(instanceId, dto.getAction(), dto.getComment()));
    }

    @Operation(summary = "撤回审批")
    @PostMapping("/{instanceId}/cancel")
    public Result<ApprovalInstance> cancel(@PathVariable Long instanceId,
                                           @RequestParam(required = false) String comment) {
        return Result.success(approvalService.cancel(instanceId, comment));
    }

    @Operation(summary = "审批进度")
    @GetMapping("/progress")
    public Result<ApprovalProgressVO> progress(@RequestParam String objectType,
                                               @RequestParam String objectNo) {
        return Result.success(approvalService.progress(objectType, objectNo));
    }

    @Operation(summary = "审批动作流水")
    @GetMapping("/{instanceId}/history")
    public Result<List<ApprovalTask>> history(@PathVariable Long instanceId) {
        return Result.success(approvalService.history(instanceId));
    }
}
