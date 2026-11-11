package com.hjgd.plm.project.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeEvidence;
import com.hjgd.plm.project.service.ProjectProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "研发项目-进度跟踪")
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectProgressController {

    private final ProjectProgressService service;

    @Operation(summary = "进度节点矩阵(22节点)")
    @GetMapping("/{projectId}/nodes")
    public Result<Map<String, Object>> matrix(@PathVariable Long projectId) {
        return Result.success(service.matrix(projectId));
    }

    @Operation(summary = "更新进度节点")
    @OperationLog(value = "更新进度节点")
    @PutMapping("/{projectId}/nodes/{code}")
    public Result<ProjectNode> updateNode(@PathVariable Long projectId,
                                          @PathVariable String code,
                                          @RequestBody ProjectNode req) {
        return Result.success(service.updateNode(projectId, code, req));
    }

    @Operation(summary = "追加子项节点(如模具重开记录): 仅项目负责人/管理员")
    @OperationLog(value = "追加子项节点")
    @PostMapping("/{projectId}/nodes/{parentCode}/child")
    public Result<ProjectNode> addChildNode(@PathVariable Long projectId,
                                            @PathVariable String parentCode,
                                            @RequestBody ProjectNode req) {
        return Result.success(service.addChildNode(projectId, parentCode, req));
    }

    @Operation(summary = "提交计划审批(工程师)")
    @OperationLog(value = "提交计划审批")
    @PostMapping("/{projectId}/nodes/{code}/plan/submit")
    public Result<ProjectNode> submitPlan(@PathVariable Long projectId, @PathVariable String code) {
        return Result.success(service.submitPlan(projectId, code));
    }

    @Operation(summary = "计划审批通过(经理)")
    @OperationLog(value = "计划审批通过")
    @PostMapping("/{projectId}/nodes/{code}/plan/approve")
    public Result<ProjectNode> approvePlan(@PathVariable Long projectId, @PathVariable String code) {
        return Result.success(service.approvePlan(projectId, code));
    }

    @Operation(summary = "计划审批驳回(经理)")
    @OperationLog(value = "计划审批驳回")
    @PostMapping("/{projectId}/nodes/{code}/plan/reject")
    public Result<ProjectNode> rejectPlan(@PathVariable Long projectId, @PathVariable String code,
                                          @RequestBody(required = false) java.util.Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return Result.success(service.rejectPlan(projectId, code, note));
    }

    @Operation(summary = "提交完成审批(工程师, 需先有证据)")
    @OperationLog(value = "提交完成审批")
    @PostMapping("/{projectId}/nodes/{code}/completion/submit")
    public Result<ProjectNode> submitCompletion(@PathVariable Long projectId, @PathVariable String code) {
        return Result.success(service.submitCompletion(projectId, code));
    }

    @Operation(summary = "完成审批通过(经理)")
    @OperationLog(value = "完成审批通过")
    @PostMapping("/{projectId}/nodes/{code}/completion/approve")
    public Result<ProjectNode> approveCompletion(@PathVariable Long projectId, @PathVariable String code) {
        return Result.success(service.approveCompletion(projectId, code));
    }

    @Operation(summary = "完成审批驳回(经理)")
    @OperationLog(value = "完成审批驳回")
    @PostMapping("/{projectId}/nodes/{code}/completion/reject")
    public Result<ProjectNode> rejectCompletion(@PathVariable Long projectId, @PathVariable String code,
                                                @RequestBody(required = false) java.util.Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return Result.success(service.rejectCompletion(projectId, code, note));
    }

    @Operation(summary = "计划/完成待审批节点(审批待办)")
    @GetMapping("/approvals/pending")
    public Result<java.util.List<java.util.Map<String, Object>>> pendingApprovals() {
        return Result.success(service.pendingApprovals());
    }

    @Operation(summary = "节点证据文件列表")
    @GetMapping("/nodes/{nodeId}/evidence")
    public Result<List<ProjectNodeEvidence>> evidence(@PathVariable Long nodeId) {
        return Result.success(service.listEvidence(nodeId));
    }

    @Operation(summary = "上传节点证据文件")
    @OperationLog(value = "上传节点证据")
    @PostMapping("/nodes/{nodeId}/evidence")
    public Result<ProjectNodeEvidence> upload(@PathVariable Long nodeId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String docType,
                                              @RequestParam(required = false) String note) {
        return Result.success(service.uploadEvidence(nodeId, file, docType, note));
    }

    @Operation(summary = "删除节点证据文件")
    @OperationLog(value = "删除节点证据")
    @DeleteMapping("/nodes/evidence/{evidenceId}")
    public Result<Void> deleteEvidence(@PathVariable Long evidenceId) {
        service.deleteEvidence(evidenceId);
        return Result.success();
    }

    @Operation(summary = "系统内填写证据")
    @OperationLog(value = "填写节点证据")
    @PostMapping("/nodes/{nodeId}/evidence/text")
    public Result<ProjectNodeEvidence> createTextEvidence(@PathVariable Long nodeId,
                                                          @RequestBody TextEvidenceReq req) {
        return Result.success(service.createTextEvidence(nodeId, req.getDocType(), req.getNote(), req.getContent()));
    }

    @Operation(summary = "修改证据(操作填写)")
    @OperationLog(value = "修改节点证据")
    @PutMapping("/nodes/evidence/{evidenceId}")
    public Result<ProjectNodeEvidence> updateEvidence(@PathVariable Long evidenceId,
                                                      @RequestBody ProjectNodeEvidence req) {
        return Result.success(service.updateEvidence(evidenceId, req));
    }

    @Operation(summary = "证据详情")
    @GetMapping("/nodes/evidence/{evidenceId}")
    public Result<ProjectNodeEvidence> getEvidence(@PathVariable Long evidenceId) {
        return Result.success(service.getEvidence(evidenceId));
    }

    @Operation(summary = "证据台账分页")
    @GetMapping("/evidence/list")
    public Result<com.hjgd.plm.common.PageResult<ProjectNodeEvidence>> evidenceList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String nodeCode,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.evidencePage(pageNum, pageSize, projectId, nodeCode, docType, source, keyword));
    }

    @Operation(summary = "证据分析统计")
    @GetMapping("/evidence/stats")
    public Result<Map<String, Object>> evidenceStats(@RequestParam(required = false) Long projectId) {
        return Result.success(service.evidenceStats(projectId));
    }

    @Operation(summary = "项目进度自检(健康分+问题清单)")
    @GetMapping("/{projectId}/progress-check")
    public Result<Map<String, Object>> progressCheck(@PathVariable Long projectId) {
        return Result.success(service.selfCheck(projectId));
    }

    @Operation(summary = "健康分口径定义(定义式/权重/取数来源)")
    @GetMapping("/progress-caliber")
    public Result<Map<String, Object>> caliber() {
        return Result.success(ProjectProgressService.caliber());
    }

    @lombok.Data
    public static class TextEvidenceReq {
        private String docType;
        private String note;
        private String content;
    }
}
