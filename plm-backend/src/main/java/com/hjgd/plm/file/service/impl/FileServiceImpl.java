package com.hjgd.plm.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.enums.FileVisibility;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.service.FileService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final PlmFileMapper fileMapper;

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

    @Override
    public void markObsolete(Long id) {
        PlmFile f = getById(id);
        f.setObsolete(1);
        fileMapper.updateById(f);
        log.info("文件[{}]标记为作废(旧版图纸自动锁定)", f.getFileName());
    }

    @Override
    public void delete(Long id) {
        fileMapper.deleteById(id);
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
