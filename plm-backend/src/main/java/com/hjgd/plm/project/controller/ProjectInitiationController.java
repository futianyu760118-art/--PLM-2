package com.hjgd.plm.project.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.project.entity.ProjectInitiation;
import com.hjgd.plm.project.service.ProjectInitiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "研发项目-立项申请书")
@RestController
@RequestMapping({"/initiation", "/v1/initiations"})
@RequiredArgsConstructor
public class ProjectInitiationController {

    private final ProjectInitiationService service;

    @Operation(summary = "立项申请书分页")
    @GetMapping("/list")
    public Result<PageResult<ProjectInitiation>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String stage,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(service.page(pageNum, pageSize, status, stage, keyword));
    }

    @Operation(summary = "立项统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(service.stats());
    }

    @Operation(summary = "审批阶段定义")
    @GetMapping("/stages")
    public Result<Map<String, String>> stages() {
        return Result.success(service.stageNames());
    }

    @Operation(summary = "立项申请书详情")
    @GetMapping("/{id}")
    public Result<ProjectInitiation> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "按项目查询立项申请书")
    @GetMapping("/by-project/{projectId}")
    public Result<ProjectInitiation> byProject(@PathVariable Long projectId) {
        return Result.success(service.getByProjectId(projectId));
    }

    @Operation(summary = "新建立项申请书")
    @OperationLog(value = "新建立项申请书")
    @PostMapping
    public Result<ProjectInitiation> create(@RequestBody ProjectInitiation req) {
        return Result.success(service.create(req));
    }

    @Operation(summary = "修改立项申请书")
    @OperationLog(value = "修改立项申请书")
    @PutMapping
    public Result<ProjectInitiation> update(@RequestBody ProjectInitiation req) {
        return Result.success(service.update(req));
    }

    @Operation(summary = "推进审批阶段")
    @OperationLog(value = "立项审批推进")
    @PostMapping("/{id}/advance")
    public Result<ProjectInitiation> advance(@PathVariable Long id,
                                             @RequestBody(required = false) ProjectInitiationService.AdvanceReq req) {
        return Result.success(service.advance(id, req == null ? new ProjectInitiationService.AdvanceReq() : req));
    }

    @Operation(summary = "驳回立项申请")
    @OperationLog(value = "立项驳回")
    @PostMapping("/{id}/reject")
    public Result<ProjectInitiation> reject(@PathVariable Long id,
                                            @RequestBody(required = false) RejectReq req) {
        return Result.success(service.reject(id, req == null ? null : req.getOpinion()));
    }

    @Operation(summary = "批准并转为研发项目")
    @OperationLog(value = "立项批准转项目")
    @PostMapping("/{id}/approve-to-project")
    public Result<Map<String, Object>> approveToProject(@PathVariable Long id,
                                                        @RequestBody(required = false) ProjectInitiationService.ApproveReq req) {
        return Result.success(service.approveToProject(id, req));
    }

    @Operation(summary = "删除立项申请书")
    @OperationLog(value = "删除立项申请书")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @Data
    public static class RejectReq {
        private String opinion;
    }
}
