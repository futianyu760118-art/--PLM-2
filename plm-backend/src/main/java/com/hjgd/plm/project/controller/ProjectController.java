package com.hjgd.plm.project.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectGateLog;
import com.hjgd.plm.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "V1 项目NPI")
@RestController
@RequestMapping({"/v1/projects", "/project"})
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "项目分页")
    @GetMapping({"", "/page"})
    public Result<PageResult<Project>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.success(projectService.page(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "项目详情")
    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @Operation(summary = "新建项目")
    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.success(projectService.create(project));
    }

    @Operation(summary = "修改项目")
    @PutMapping
    public Result<Project> update(@RequestBody Project project) {
        return Result.success(projectService.update(project));
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    @Operation(summary = "阶段门定义")
    @GetMapping("/gates/defs")
    public Result<Map<String, Object>> gateDefs() {
        return Result.success(Map.of("codes", projectService.getGateDefinitions()));
    }

    @Operation(summary = "阶段门流转记录")
    @GetMapping("/{projectId}/gates")
    public Result<List<ProjectGateLog>> gateLogs(@PathVariable Long projectId) {
        return Result.success(projectService.getGateLogs(projectId));
    }

    @Operation(summary = "通过阶段门")
    @PostMapping("/{projectId}/gates/{gateCode}/pass")
    public Result<ProjectGateLog> passGate(@PathVariable Long projectId,
                                           @PathVariable String gateCode,
                                           @RequestBody(required = false) GateActionReq req) {
        String comment = req == null ? null : req.getComment();
        String evidence = req == null ? null : req.getEvidenceRef();
        return Result.success(projectService.passGate(projectId, gateCode, comment, evidence));
    }

    @Operation(summary = "驳回阶段门")
    @PostMapping("/{projectId}/gates/{gateCode}/fail")
    public Result<ProjectGateLog> failGate(@PathVariable Long projectId,
                                           @PathVariable String gateCode,
                                           @RequestBody(required = false) GateActionReq req) {
        String comment = req == null ? null : req.getComment();
        return Result.success(projectService.failGate(projectId, gateCode, comment));
    }

    @Data
    public static class GateActionReq {
        private String comment;
        private String evidenceRef;
    }
}
