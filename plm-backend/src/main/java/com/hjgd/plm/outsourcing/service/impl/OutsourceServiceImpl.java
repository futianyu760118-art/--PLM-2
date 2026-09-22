package com.hjgd.plm.outsourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.file.watermark.WatermarkConfig;
import com.hjgd.plm.file.watermark.WatermarkEngine;
import com.hjgd.plm.outsourcing.dto.OutsourceRequestDTO;
import com.hjgd.plm.outsourcing.entity.OutsourceFile;
import com.hjgd.plm.outsourcing.entity.OutsourceRequest;
import com.hjgd.plm.outsourcing.mapper.OutsourceFileMapper;
import com.hjgd.plm.outsourcing.mapper.OutsourceRequestMapper;
import com.hjgd.plm.outsourcing.service.OutsourceService;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceServiceImpl implements OutsourceService {

    private final OutsourceRequestMapper requestMapper;
    private final OutsourceFileMapper fileMapper;
    private final SequenceService sequenceService;
    private final FileService fileService;
    private final WatermarkEngine watermarkEngine;
    private final com.hjgd.plm.file.mapper.PlmFileMapper fileMapperBean;

    @Value("${plm.file.intranet-dir}")
    private String intranetDir;

    @Override
    public PageResult<OutsourceRequest> page(Integer pageNum, Integer pageSize, String company, String status) {
        LambdaQueryWrapper<OutsourceRequest> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(company), OutsourceRequest::getOutsourceCompany, company)
                .eq(StringUtils.hasText(status), OutsourceRequest::getStatus, status)
                .orderByDesc(OutsourceRequest::getCreatedAt);
        Page<OutsourceRequest> page = requestMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(page);
    }

    @Override
    public OutsourceRequest getById(Long id) {
        OutsourceRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return req;
    }

    @Override
    @Transactional
    public OutsourceRequest create(OutsourceRequestDTO dto) {
        OutsourceRequest req = new OutsourceRequest();
        req.setRequestNo(sequenceService.nextNo("OUTSOURCE_NO"));
        req.setOutsourceCompany(dto.getOutsourceCompany());
        req.setContactPerson(dto.getContactPerson());
        req.setPurpose(dto.getPurpose());
        req.setDrawingType(dto.getDrawingType());
        req.setValidityDays(dto.getValidityDays());
        req.setDescription(dto.getDescription());
        req.setStatus("DRAFT");
        req.setApplicant(SecurityUtils.getCurrentRealName());
        req.setCreatedBy(SecurityUtils.getCurrentRealName());
        requestMapper.insert(req);
        if (dto.getFileIds() != null) {
            for (Long fileId : dto.getFileIds()) {
                OutsourceFile of = new OutsourceFile();
                of.setRequestId(req.getId());
                of.setFileId(fileId);
                fileMapper.insert(of);
            }
        }
        log.info("外协发图申请创建: {} 外协={}", req.getRequestNo(), req.getOutsourceCompany());
        return req;
    }

    @Override
    @Transactional
    public void submit(Long id) {
        OutsourceRequest req = getById(id);
        if (!"DRAFT".equals(req.getStatus()) && !"REJECTED".equals(req.getStatus())) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        req.setStatus("PENDING");
        requestMapper.updateById(req);
    }

    @Override
    @Transactional
    public void approve(Long id, String comment) {
        OutsourceRequest req = getById(id);
        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        req.setStatus("APPROVED");
        req.setApprover(SecurityUtils.getCurrentRealName());
        req.setApproveTime(LocalDateTime.now());
        req.setApproveComment(comment);
        req.setExpireAt(LocalDateTime.now().plusDays(req.getValidityDays()));
        requestMapper.updateById(req);
        generateWatermarkedFiles(req);
        log.info("外协申请[{}]审批通过,有效期至{}", req.getRequestNo(), req.getExpireAt());
    }

    @Override
    @Transactional
    public void reject(Long id, String comment) {
        OutsourceRequest req = getById(id);
        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        req.setStatus("REJECTED");
        req.setApprover(SecurityUtils.getCurrentRealName());
        req.setApproveTime(LocalDateTime.now());
        req.setApproveComment(comment);
        requestMapper.updateById(req);
    }

    @Override
    public List<OutsourceFile> getFiles(Long requestId) {
        return fileMapper.selectList(
                new LambdaQueryWrapper<OutsourceFile>().eq(OutsourceFile::getRequestId, requestId));
    }

    @Override
    @Transactional
    public void download(Long requestId, Long fileId) {
        OutsourceRequest req = getById(requestId);
        if (!"APPROVED".equals(req.getStatus())) {
            throw new BusinessException("申请未审批通过,禁止下载");
        }
        if (req.getExpireAt() != null && req.getExpireAt().isBefore(LocalDateTime.now())) {
            req.setStatus("EXPIRED");
            requestMapper.updateById(req);
            throw new BusinessException("文件已过期,无法下载");
        }
        OutsourceFile of = fileMapper.selectOne(new LambdaQueryWrapper<OutsourceFile>()
                .eq(OutsourceFile::getRequestId, requestId)
                .eq(OutsourceFile::getFileId, fileId));
        if (of == null) {
            throw new BusinessException("文件不在外协包内");
        }
        of.setDownloadCount(of.getDownloadCount() == null ? 1 : of.getDownloadCount() + 1);
        of.setLastDownloadAt(LocalDateTime.now());
        fileMapper.updateById(of);
        Long actualFileId = of.getWatermarkedFileId() != null ? of.getWatermarkedFileId() : fileId;
        PlmFile f = fileService.getById(actualFileId);
        log.info("外协下载留痕: 申请{} 文件{} 水印版={} 外协={} 操作人={}", req.getRequestNo(), f.getFileName(), f.getHasWatermark() == 1, req.getOutsourceCompany(), SecurityUtils.getCurrentUsername());
    }

    private void generateWatermarkedFiles(OutsourceRequest req) {
        List<OutsourceFile> files = getFiles(req.getId());
        WatermarkConfig wc = WatermarkConfig.forOutsource(
                req.getOutsourceCompany(),
                SecurityUtils.getCurrentUsername(),
                "system",
                req.getValidityDays());
        File wmDir = new File(intranetDir, "watermarked/" + req.getRequestNo());
        if (!wmDir.exists()) {
            wmDir.mkdirs();
        }
        int success = 0;
        for (OutsourceFile of : files) {
            PlmFile original = fileService.getById(of.getFileId());
            try {
                File source = resolveDiskFile(original.getFilePath());
                if (source == null || !source.exists()) {
                    log.warn("源文件不存在,跳过: {}", original.getFileName());
                    continue;
                }
                File wmOutput = new File(wmDir, UUID.randomUUID().toString().replace("-", "")
                        + original.getFileExt());
                watermarkEngine.apply(source, wmOutput, wc);
                PlmFile wmFile = new PlmFile();
                wmFile.setFileName("WM_" + original.getFileName());
                wmFile.setFilePath("intranet/watermarked/" + req.getRequestNo() + "/" + wmOutput.getName());
                wmFile.setFileExt(original.getFileExt());
                wmFile.setFileSize(wmOutput.length());
                wmFile.setFileType(original.getFileType());
                wmFile.setVisibility("OUTSOURCE");
                wmFile.setHasWatermark(1);
                wmFile.setPartNo(original.getPartNo());
                wmFile.setExpireAt(req.getExpireAt());
                wmFile.setUploadedBy(SecurityUtils.getCurrentRealName());
                wmFile.setUploadedAt(LocalDateTime.now());
                fileMapperBean.insert(wmFile);
                of.setWatermarkedFileId(wmFile.getId());
                fileMapper.updateById(of);
                success++;
            } catch (Exception e) {
                log.error("水印文件生成失败: {}", original.getFileName(), e);
            }
        }
        log.info("外协包[{}]水印文件生成完成: {}/{} 成功 (企业水印+{}+有效期{}天)", req.getRequestNo(), success, files.size(), req.getOutsourceCompany(), req.getValidityDays());
    }

    private File resolveDiskFile(String path) {
        if (path == null) return null;
        if (path.startsWith("intranet/")) {
            return new File(intranetDir, path.substring("intranet/".length()));
        } else if (path.startsWith("extranet/")) {
            return new File(intranetDir, path.substring("extranet/".length()));
        }
        return new File(path);
    }
}
