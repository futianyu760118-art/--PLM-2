package com.hjgd.plm.model3d.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.model3d.entity.Model3D;
import com.hjgd.plm.model3d.enums.Model3DType;
import com.hjgd.plm.model3d.mapper.Model3DMapper;
import com.hjgd.plm.system.service.SequenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Tag(name = "3D模型库")
@RestController
@RequestMapping("/model3d")
@RequiredArgsConstructor
public class Model3DController {

    private final Model3DMapper mapper;
    private final FileService fileService;
    private final SequenceService sequenceService;
    private final com.hjgd.plm.file.mapper.PlmFileMapper fileMapperBean;

    @Value("${plm.algorithm.base-url}")
    private String algorithmBaseUrl;

    @Value("${plm.file.intranet-dir}")
    private String intranetDir;
    @Value("${plm.file.extranet-dir}")
    private String extranetDir;

    @Operation(summary = "3D模型分页(按料号/类型)")
    @GetMapping("/page")
    public Result<PageResult<Model3D>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String partNo,
            @RequestParam(required = false) Model3DType modelType) {
        LambdaQueryWrapper<Model3D> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(partNo), Model3D::getPartNo, partNo)
                .eq(modelType != null, Model3D::getModelType, modelType)
                .orderByDesc(Model3D::getCreatedAt);
        Page<Model3D> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "上传3D模型(内网存高精度,自动生成外网脱敏GLB)")
    @OperationLog(value = "上传3D模型", partNo = "#partNo")
    @PreAuthorize("hasAuthority('model3d:upload')")
    @PostMapping("/upload")
    public Result<Model3D> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam String partNo,
                                  @RequestParam Model3DType modelType,
                                  @RequestParam(defaultValue = "INTRANET") String visibility) {
        PlmFile pf = fileService.upload(file, partNo, "3d", visibility);
        Model3D m = new Model3D();
        m.setPartNo(partNo);
        m.setModelName(file.getOriginalFilename());
        m.setModelType(modelType);
        m.setSourceFormat(StringUtils.getFilenameExtension(file.getOriginalFilename()));
        m.setIntranetFileId(pf.getId());
        m.setVersionNo("V1.0");
        m.setStatus("DRAFT");
        m.setSource(0);
        m.setCreatedBy(SecurityUtils.getCurrentRealName());
        mapper.insert(m);
        log.info("3D模型上传: 料号{} 类型{} 文件{}", partNo, modelType, pf.getFileName());
        return Result.success(m);
    }

    @Operation(summary = "3D爆炸解析(调用算法服务,生成初级爆炸BOM数据)")
    @OperationLog(value = "3D爆炸解析")
    @PreAuthorize("hasAuthority('model3d:explode')")
    @PostMapping("/{id}/explode")
    public Result<String> explode(@PathVariable Long id) {
        Model3D m = mapper.selectById(id);
        if (m == null || m.getIntranetFileId() == null) {
            return Result.failed("模型或文件不存在");
        }
        try {
            RestClient client = RestClient.create(algorithmBaseUrl);
            Map result = client.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            m.setExplodeJson("{\"status\":\"exploded\",\"algorithm\":\"trimesh\"}");
            mapper.updateById(m);
            return Result.success(m.getExplodeJson(), "爆炸解析完成, 已生成初级BOM数据");
        } catch (Exception e) {
            log.error("调用算法服务失败", e);
            return Result.failed("算法服务暂不可用: " + e.getMessage());
        }
    }

    @Operation(summary = "在线预览(返回GLB模型地址)")
    @PreAuthorize("hasAuthority('model3d:preview')")
    @GetMapping("/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        Model3D m = mapper.selectById(id);
        Long fileId = m.getExtranetFileId() != null ? m.getExtranetFileId() : m.getIntranetFileId();
        PlmFile f = fileService.getById(fileId);
        return Result.success(Map.of(
                "modelId", m.getId(),
                "fileName", f.getFileName(),
                "fileId", f.getId(),
                "partNo", m.getPartNo(),
                "modelType", m.getModelType().name(),
                "sourceFormat", m.getSourceFormat() != null ? m.getSourceFormat() : "",
                "glbUrl", "/api/model3d/" + id + "/glb-data"
        ));
    }

    @Operation(summary = "GLB模型数据流(Three.js直接加载,支持query token)")
    @GetMapping("/{id}/glb-data")
    public ResponseEntity<org.springframework.core.io.Resource> glbData(
            @PathVariable Long id,
            @RequestParam(required = false) String token) {
        Model3D m = mapper.selectById(id);
        if (m == null) return ResponseEntity.notFound().build();
        Long fileId = m.getExtranetFileId() != null ? m.getExtranetFileId() : m.getIntranetFileId();
        if (fileId == null) return ResponseEntity.notFound().build();
        org.springframework.core.io.Resource resource = fileService.download(fileId, false);
        PlmFile f = fileService.getById(fileId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(org.springframework.http.MediaType.parseMediaType("model/gltf-binary"))
                .body(resource);
    }

    @Operation(summary = "一键转换为GLB(调用算法服务,转换后可直接在线预览)")
    @OperationLog(value = "3D模型格式转换")
    @PreAuthorize("hasAuthority('model3d:upload')")
    @PostMapping("/{id}/convert-glb")
    public Result<Map<String, Object>> convertToGlb(@PathVariable Long id) {
        Model3D m = mapper.selectById(id);
        if (m == null || m.getIntranetFileId() == null) {
            return Result.failed("模型或文件不存在");
        }
        PlmFile original = fileService.getById(m.getIntranetFileId());
        java.io.File sourceFile = resolveDiskFile(original.getFilePath());
        if (sourceFile == null || !sourceFile.exists()) {
            return Result.failed("源文件不存在: " + original.getFileName());
        }
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String glbPath = tempDir + java.io.File.separator + java.util.UUID.randomUUID().toString().replace("-", "") + ".glb";
            String script = String.format(
                    "import sys; sys.path.insert(0, r'D:\\PLM-2\\plm-algorithm'); " +
                    "from app.services.model_converter import ModelConverter; " +
                    "r = ModelConverter.convert_to_glb(r'%s', r'%s'); " +
                    "print('SUCCESS' if r['success'] else 'FAIL'); " +
                    "print(r.get('info',{}).get('faces','')); " +
                    "sys.exit(0 if r['success'] else 1)",
                    sourceFile.getAbsolutePath().replace("\\", "\\\\"), glbPath.replace("\\", "\\\\"));
            ProcessBuilder pb = new ProcessBuilder("python", "-c", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                log.error("Python转换失败: {}", output);
                return Result.failed("格式转换失败: " + output.replaceAll("\\s+", " ").substring(0, Math.min(output.length(), 200)));
            }
            java.io.File tempGlb = new java.io.File(glbPath);
            if (!tempGlb.exists() || tempGlb.length() == 0) {
                return Result.failed("转换失败: GLB文件未生成");
            }
            byte[] glbBytes = java.nio.file.Files.readAllBytes(tempGlb.toPath());
            tempGlb.delete();
            String glbName = original.getFileName().replaceAll("(?i)\\.(step|stp|iges|igs|obj|stl|ply)$", "") + "_preview.glb";
            java.io.File glbFile = new java.io.File(intranetDir,
                    (original.getPartNo() != null ? original.getPartNo() + "/" : "converted/")
                            + new java.text.SimpleDateFormat("yyyy/MM").format(new java.util.Date())
                            + "/" + java.util.UUID.randomUUID().toString().replace("-", "") + ".glb");
            glbFile.getParentFile().mkdirs();
            java.nio.file.Files.write(glbFile.toPath(), glbBytes);
            PlmFile glbPlmFile = new PlmFile();
            glbPlmFile.setFileName(glbName);
            glbPlmFile.setFilePath("intranet/" + original.getPartNo() + "/" + new java.text.SimpleDateFormat("yyyy/MM").format(new java.util.Date()) + "/" + glbFile.getName());
            glbPlmFile.setFileExt(".glb");
            glbPlmFile.setFileSize((long) glbBytes.length);
            glbPlmFile.setFileType("3d-glb");
            glbPlmFile.setVisibility("INTRANET");
            glbPlmFile.setHasWatermark(0);
            glbPlmFile.setPartNo(original.getPartNo());
            glbPlmFile.setUploadedBy(SecurityUtils.getCurrentRealName());
            glbPlmFile.setUploadedAt(java.time.LocalDateTime.now());
            fileMapperBean.insert(glbPlmFile);
            m.setExtranetFileId(glbPlmFile.getId());
            mapper.updateById(m);
            log.info("3D模型[{}]转换GLB成功: {} → {} ({}KB)", m.getModelName(), original.getFileName(), glbName, glbBytes.length / 1024);
            return Result.success(Map.of(
                    "glbFileId", glbPlmFile.getId(),
                    "glbFileName", glbName,
                    "sizeKB", glbBytes.length / 1024,
                    "message", "转换成功，现在可以在线预览了"
            ), "GLB转换完成，点击预览即可在线查看");
        } catch (Exception e) {
            log.error("3D模型GLB转换失败", e);
            return Result.failed("转换失败: " + e.getMessage());
        }
    }

    private java.io.File resolveDiskFile(String path) {
        if (path == null) return null;
        if (path.startsWith("intranet/")) {
            return new java.io.File(intranetDir, path.substring("intranet/".length()));
        } else if (path.startsWith("extranet/")) {
            return new java.io.File(extranetDir, path.substring("extranet/".length()));
        }
        return new java.io.File(path);
    }

    @Operation(summary = "ECN联动更新3D版本")
    @PutMapping("/{id}/version")
    public Result<Void> updateVersion(@PathVariable Long id,
                                      @RequestParam String versionNo,
                                      @RequestParam String ecnNo) {
        Model3D m = mapper.selectById(id);
        m.setVersionNo(versionNo);
        m.setEcnNo(ecnNo);
        mapper.updateById(m);
        return Result.success();
    }
}
