package com.hjgd.plm.exportx.controller;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.exportx.entity.ExportTask;
import com.hjgd.plm.exportx.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Tag(name = "V1 通用导出")
@RestController
@RequestMapping("/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Operation(summary = "创建导出任务")
    @PostMapping
    public Result<ExportTask> create(@RequestBody ExportReq req) {
        ExportTask task = exportService.create(
                req.getExportType(),
                req.getFormat(),
                req.getName(),
                req.getSpec(),
                SecurityUtils.getCurrentRealName());
        exportService.execute(task.getId());
        return Result.success(exportService.getById(task.getId()));
    }

    @Operation(summary = "查询导出任务")
    @GetMapping
    public Result<List<ExportTask>> list(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "20") int limit) {
        return Result.success(exportService.list(status, limit));
    }

    @Operation(summary = "导出任务详情")
    @GetMapping("/{id}")
    public Result<ExportTask> get(@PathVariable Long id) {
        return Result.success(exportService.getById(id));
    }

    @Operation(summary = "下载导出文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        ExportTask task = exportService.getById(id);
        if (task == null || !"DONE".equals(task.getStatus()) || task.getFilePath() == null) {
            return ResponseEntity.status(404).body(Map.of("error", "文件不存在或未完成"));
        }
        Path path = exportService.resolveFilePath(task.getFilePath());
        FileSystemResource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + task.getExportNo() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    @Data
    public static class ExportReq {
        private String exportType;
        private String format;
        private String name;
        private Map<String, Object> spec;
    }
}
