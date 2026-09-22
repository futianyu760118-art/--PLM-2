package com.hjgd.plm.archive.controller;

import com.hjgd.plm.archive.entity.ArchiveFile;
import com.hjgd.plm.archive.entity.ArchiveTreeNode;
import com.hjgd.plm.archive.service.ArchiveTreeService;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "档案管理")
@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveTreeService archiveTreeService;

    @Operation(summary = "获取料号固定目录树")
    @GetMapping("/tree/{partNo}")
    public Result<List<ArchiveTreeNode>> tree(@PathVariable String partNo) {
        return Result.success(archiveTreeService.getTree(partNo));
    }

    @Operation(summary = "为料号生成固定目录树(物料创建时自动触发)")
    @PostMapping("/generate/{partNo}")
    public Result<Void> generate(@PathVariable String partNo) {
        archiveTreeService.generateForPart(partNo);
        return Result.success();
    }

    @Operation(summary = "挂载文件到目录节点")
    @PostMapping("/attach")
    public Result<Void> attach(@RequestParam String partNo,
                               @RequestParam String nodeCode,
                               @RequestParam Long fileId) {
        archiveTreeService.attachFile(partNo, nodeCode, fileId);
        return Result.success();
    }

    @Operation(summary = "查看目录节点下文件列表")
    @GetMapping("/node/{nodeId}/files")
    public Result<List<ArchiveFile>> files(@PathVariable Long nodeId) {
        return Result.success(archiveTreeService.listFiles(nodeId));
    }

    @Operation(summary = "移除文件挂载")
    @DeleteMapping("/file/{archiveFileId}")
    public Result<Void> removeFile(@PathVariable Long archiveFileId) {
        archiveTreeService.removeFile(archiveFileId);
        return Result.success();
    }
}
