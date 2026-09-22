package com.hjgd.plm.integration.controller;

import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * 算法服务代理 (前端统一通过主后端调用 Python 算法服务)
 * 避免前端直接访问算法服务, 便于权限管控
 */
@Slf4j
@Tag(name = "算法服务代理")
@RestController
@RequestMapping("/algorithm")
@RequiredArgsConstructor
public class AlgorithmProxyController {

    @Value("${plm.algorithm.base-url}")
    private String baseUrl;

    @Operation(summary = "2D 图纸自动分解 (代理调用 Python 算法服务)")
    @PostMapping(value = "/drawing/decompose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> decomposeDrawing(@RequestParam("file") MultipartFile file) {
        try {
            File temp = Files.createTempFile("plm_drawing_", "_" + file.getOriginalFilename()).toFile();
            file.transferTo(temp);
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(temp));

            Map result = RestClient.create(baseUrl).post()
                    .uri("/api/drawing/decompose")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(Map.class);
            temp.delete();
            return Result.success(result != null ? result.get("data") : null,
                    result != null ? result.getOrDefault("message", "分解完成").toString() : "分解完成");
        } catch (Exception e) {
            log.error("2D图纸分解失败", e);
            return Result.failed("图纸分解失败(算法服务不可用或文件格式不支持): " + e.getMessage());
        }
    }

    @Operation(summary = "图纸分类标准(8类)")
    @GetMapping("/drawing/categories")
    public Result<Object> categories() {
        try {
            Map result = RestClient.create(baseUrl).get()
                    .uri("/api/drawing/categories")
                    .retrieve()
                    .body(Map.class);
            return Result.success(result != null ? result.get("data") : null);
        } catch (Exception e) {
            return Result.failed("算法服务不可用");
        }
    }

    @Operation(summary = "3D模型解析")
    @PostMapping(value = "/model3d/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> parseModel3d(@RequestParam("file") MultipartFile file) {
        try {
            File temp = Files.createTempFile("plm_model_", "_" + file.getOriginalFilename()).toFile();
            file.transferTo(temp);
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(temp));
            Map result = RestClient.create(baseUrl).post()
                    .uri("/api/model3d/parse")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(Map.class);
            temp.delete();
            return Result.success(result != null ? result.get("data") : null,
                    result != null ? result.getOrDefault("message", "解析完成").toString() : "解析完成");
        } catch (Exception e) {
            log.error("3D模型解析失败", e);
            return Result.failed("3D解析失败(算法服务不可用): " + e.getMessage());
        }
    }

    @Operation(summary = "AI 建模 - 文字生成3D")
    @PostMapping("/ai-modeling/text-to-3d")
    public Result<Object> textTo3d(@RequestBody Map<String, Object> body) {
        try {
            Map result = RestClient.create(baseUrl).post()
                    .uri("/api/ai-modeling/text-to-3d")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return Result.success(result != null ? result.get("data") : null, "AI建模任务已提交");
        } catch (Exception e) {
            return Result.failed("AI建模平台调用失败: " + e.getMessage());
        }
    }

    @Operation(summary = "算法服务健康检查")
    @GetMapping("/health")
    public Result<Object> health() {
        try {
            Map result = RestClient.create(baseUrl).get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return Result.success(result);
        } catch (Exception e) {
            return Result.failed("算法服务不可用");
        }
    }
}
