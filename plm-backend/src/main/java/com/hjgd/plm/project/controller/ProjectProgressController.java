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

    @Operation(summary = "进度节点矩阵(19节点)")
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

    @lombok.Data
    public static class TextEvidenceReq {
        private String docType;
        private String note;
        private String content;
    }
}
