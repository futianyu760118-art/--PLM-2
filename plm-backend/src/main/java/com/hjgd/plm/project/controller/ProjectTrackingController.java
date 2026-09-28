package com.hjgd.plm.project.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.service.ProjectTrackingService;
import com.hjgd.plm.rd.service.NodeRollupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 研发项目跟踪总表 (EBMS 研发中心「研发项目跟踪」复制)。
 * 一行一项目, 列为 22 节点, 单元格值: V/X/进行中/待定/日期/文字。
 */
@Tag(name = "研发自治中心-项目跟踪")
@RestController
@RequestMapping({"/v1/rd/tracking", "/rd/tracking"})
@RequiredArgsConstructor
public class ProjectTrackingController {

    private final ProjectTrackingService trackingService;
    private final NodeRollupService rollupService;

    @Operation(summary = "跟踪表列定义(22节点)")
    @GetMapping("/node-defs")
    public Result<List<Map<String, Object>>> nodeDefs() {
        return Result.success(trackingService.nodeDefs());
    }

    @Operation(summary = "节点↔模板工作表 对照表")
    @GetMapping("/node-sheet-map")
    public Result<List<Map<String, Object>>> nodeSheetMap() {
        return Result.success(trackingService.nodeSheetMap());
    }

    @Operation(summary = "跟踪总表(跨项目)")
    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String nodeStatus) {
        return Result.success(trackingService.page(pageNum, pageSize, keyword, status, nodeStatus));
    }

    @Operation(summary = "完成判定汇总: 全部项目(工作表/台账 -> 节点)")
    @PostMapping("/rollup")
    public Result<Map<String, Object>> rollupAll() {
        return Result.success(rollupService.rollupAll());
    }

    @Operation(summary = "完成判定汇总: 单个项目")
    @PostMapping("/{projectId}/rollup")
    public Result<Map<String, Object>> rollupOne(@PathVariable Long projectId) {
        return Result.success(rollupService.rollupProject(projectId));
    }

    @Operation(summary = "内联编辑单元格")
    @PutMapping("/{projectId}/cell")
    public Result<ProjectNode> setCell(@PathVariable Long projectId, @RequestBody CellReq body) {
        return Result.success(trackingService.setCell(projectId, body.getNodeCode(), body.getValue()));
    }

    @Data
    public static class CellReq {
        private String nodeCode;
        private String value;
    }
}
