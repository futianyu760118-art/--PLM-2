package com.hjgd.plm.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.enums.FileVisibility;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.file.watermark.WatermarkConfig;
import com.hjgd.plm.file.watermark.WatermarkEngine;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final PlmFileMapper fileMapper;
    private final WatermarkEngine watermarkEngine;
    private final MaterialMapper materialMapper;

    @Value("${plm.file.intranet-dir}")
    private String intranetDir;
    @Value("${plm.file.extranet-dir}")
    private String extranetDir;

    @Override
    public PlmFile upload(MultipartFile file, String partNo, String fileType, String visibility) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase()
                : "";
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        boolean isExtranet = "EXTERNAL".equals(visibility) || "PUBLIC".equals(visibility);
        String baseDir = isExtranet ? extranetDir : intranetDir;
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        String relativePath = (StringUtils.hasText(partNo) ? partNo + "/" : "common/") + dateDir + "/" + storedName;
        File dest = new File(baseDir, relativePath);
        if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
            throw new BusinessException("创建存储目录失败");
        }
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
        PlmFile entity = new PlmFile();
        entity.setFileName(originalName);
        entity.setFilePath((isExtranet ? "extranet/" : "intranet/") + relativePath);
        entity.setFileExt(ext);
        entity.setFileSize(file.getSize());
        entity.setFileType(fileType);
        entity.setVisibility(StringUtils.hasText(visibility) ? visibility : FileVisibility.INTRANET.name());
        entity.setHasWatermark(0);
        entity.setObsolete(0);
        entity.setPartNo(partNo);
        // 落版本号: ECN 生效时按「该料号 + 升版前版本号」定位旧图纸并作废,
        // 版本号缺失的旧行永远匹配不到, 旧图纸会一直可下载(AC1)。
        entity.setVersionNo(resolveMaterialVersion(partNo));
        entity.setUploadedBy(SecurityUtils.getCurrentRealName());
        entity.setUploadedAt(LocalDateTime.now());
        fileMapper.insert(entity);
        log.info("文件上传成功: {} -> {} (料号:{})", originalName, entity.getFilePath(), partNo);
        return entity;
    }

    @Override
    public PlmFile getById(Long id) {
        PlmFile f = fileMapper.selectById(id);
        if (f == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return f;
    }

    @Override
    public Resource download(Long id, boolean isOutsource) {
        PlmFile f = getById(id);
        if (f.getObsolete() != null && f.getObsolete() == 1) {
            throw new BusinessException("作废文件禁止下载");
        }
        if (isOutsource) {
            if (f.getExpireAt() != null && f.getExpireAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException("文件已过期,无法下载");
            }
        }
        File disk = resolveDiskFile(f.getFilePath());
        if (!disk.exists()) {
            throw new BusinessException("文件不存在");
        }
        return new FileSystemResource(disk);
    }

    /**
     * 作废 + 水印锁定 (v5 §5.2 watermark_lock)。
     *
     * 先落库 obsolete=1 / has_watermark=1 —— download() 据此立即拒绝下载;
     * 再就地盖章(写临时文件成功后原子替换), 使磁盘上残留的副本也带"作废"标识。
     * 盖章失败不回滚: 拦截靠 DB 标记, 水印是纵深防御, 磁盘/字体缺失不应阻断 ECN 生效。
     */
    @Override
    public void markObsolete(Long id) {
        PlmFile f = getById(id);
        boolean alreadyObsolete = f.getObsolete() != null && f.getObsolete() == 1;
        f.setObsolete(1);
        f.setHasWatermark(1);
        fileMapper.updateById(f);
        if (!alreadyObsolete) {
            stampObsoleteWatermark(f);
        }
        log.info("文件[{}]标记为作废并加水印(旧版图纸自动锁定)", f.getFileName());
    }

    private void stampObsoleteWatermark(PlmFile f) {
        File source = null;
        File tmp = null;
        try {
            source = resolveDiskFile(f.getFilePath());
            if (!source.isFile()) {
                log.warn("作废文件[{}]磁盘不存在, 仅落锁未盖章: {}", f.getFileName(), f.getFilePath());
                return;
            }
            WatermarkConfig cfg = WatermarkConfig.forObsolete(SecurityUtils.getCurrentRealName());
            tmp = new File(source.getParentFile(), source.getName() + ".wm.tmp");
            watermarkEngine.apply(source, tmp, cfg);
            Files.move(tmp.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("作废文件[{}]加盖水印失败(落锁仍生效): {}", f.getFileName(), e.getMessage());
        } finally {
            deleteQuietly(tmp, source);
        }
    }

    /** 清理盖章临时文件; 清理失败不影响已完成的作废落锁 */
    private void deleteQuietly(File tmp, File source) {
        if (tmp == null || tmp.equals(source)) {
            return;
        }
        try {
            Files.deleteIfExists(tmp.toPath());
        } catch (IOException e) {
            log.warn("水印临时文件清理失败: {}", tmp.getAbsolutePath(), e);
        }
    }

    @Override
    public void delete(Long id) {
        fileMapper.deleteById(id);
    }

    /** 取料号当前版本号; 料号为空或物料不存在(公共附件)时返回 null */
    private String resolveMaterialVersion(String partNo) {
        if (!StringUtils.hasText(partNo)) {
            return null;
        }
        Material m = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>().eq(Material::getPartNo, partNo));
        return m == null ? null : m.getVersionNo();
    }

    private File resolveDiskFile(String path) {
        if (path.startsWith("intranet/")) {
            return new File(intranetDir, path.substring("intranet/".length()));
        } else if (path.startsWith("extranet/")) {
            return new File(extranetDir, path.substring("extranet/".length()));
        }
        return new File(path);
    }
}
