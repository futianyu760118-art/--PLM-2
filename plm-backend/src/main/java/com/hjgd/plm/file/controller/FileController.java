package com.hjgd.plm.file.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<PlmFile> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) String partNo,
                                  @RequestParam(required = false) String fileType,
                                  @RequestParam(defaultValue = "INTRANET") String visibility) {
        return Result.success(fileService.upload(file, partNo, fileType, visibility));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{id}")
    public Result<PlmFile> get(@PathVariable Long id) {
        return Result.success(fileService.getById(id));
    }

    @Operation(summary = "下载文件(外协带水印版本)")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean isOutsource) {
        PlmFile f = fileService.getById(id);
        Resource resource = fileService.download(id, isOutsource);
        String encodedName = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "预览文件(内联, 用于系统内打开)")
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        PlmFile f = fileService.getById(id);
        Resource resource = fileService.download(id, false);
        String encodedName = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(contentType(f.getFileExt())))
                .body(resource);
    }

    /** 按扩展名推断内联预览的 Content-Type */
    private String contentType(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return switch (e) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".svg" -> "image/svg+xml";
            case ".bmp" -> "image/bmp";
            case ".txt", ".md", ".log", ".csv", ".json", ".xml" -> "text/plain; charset=UTF-8";
            case ".mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    @Operation(summary = "标记文件作废")
    @PutMapping("/{id}/obsolete")
    public Result<Void> obsolete(@PathVariable Long id) {
        fileService.markObsolete(id);
        return Result.success();
    }
}
