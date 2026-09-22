package com.hjgd.plm.share.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.model3d.entity.Model3D;
import com.hjgd.plm.model3d.mapper.Model3DMapper;
import com.hjgd.plm.share.entity.ShareAccessLog;
import com.hjgd.plm.share.entity.ShareLink;
import com.hjgd.plm.share.mapper.ShareAccessLogMapper;
import com.hjgd.plm.share.mapper.ShareLinkMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "外网分享链接")
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkMapper shareLinkMapper;
    private final ShareAccessLogMapper accessLogMapper;
    private final Model3DMapper model3DMapper;
    private final FileService fileService;

    @Operation(summary = "创建临时分享链接(外网脱敏3D预览)")
    @PostMapping
    public Result<Map<String, Object>> createShare(@RequestBody Map<String, Object> body) {
        Long model3dId = body.get("model3dId") == null ? null : Long.valueOf(body.get("model3dId").toString());
        Integer days = body.get("days") == null ? 7 : Integer.valueOf(body.get("days").toString());
        String title = (String) body.getOrDefault("title", "产品3D外观预览");

        ShareLink link = new ShareLink();
        link.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        link.setModel3dId(model3dId);
        link.setTitle(title);
        link.setCreator(SecurityUtils.getCurrentRealName());
        link.setExpireAt(LocalDateTime.now().plusDays(days));
        link.setViewCount(0);
        link.setStatus(1);
        link.setAllowDownload(0);
        link.setRemark((String) body.get("remark"));
        if (model3dId != null) {
            Model3D m = model3DMapper.selectById(model3dId);
            if (m != null) {
                link.setPartNo(m.getPartNo());
            }
        }
        shareLinkMapper.insert(link);
        log.info("外网分享链接创建: token={} 料号={} 有效期{}天", link.getShareToken(), link.getPartNo(), days);
        return Result.success(Map.of(
                "shareToken", link.getShareToken(),
                "shareUrl", "/share/" + link.getShareToken(),
                "expireAt", link.getExpireAt(),
                "title", title
        ));
    }

    @Operation(summary = "我的分享链接列表")
    @GetMapping("/list")
    public Result<List<ShareLink>> list() {
        return Result.success(shareLinkMapper.selectList(
                new LambdaQueryWrapper<ShareLink>()
                        .eq(ShareLink::getCreator, SecurityUtils.getCurrentRealName())
                        .orderByDesc(ShareLink::getCreatedAt)));
    }

    @Operation(summary = "作废分享链接")
    @PutMapping("/{id}/void")
    public Result<Void> voidShare(@PathVariable Long id) {
        ShareLink link = shareLinkMapper.selectById(id);
        link.setStatus(0);
        shareLinkMapper.updateById(link);
        return Result.success();
    }

    @Operation(summary = "公开访问入口(无需登录,校验时效)")
    @GetMapping("/public/{token}")
    public Result<Map<String, Object>> access(@PathVariable String token, HttpServletRequest request) {
        ShareLink link = validateShare(token, request);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("title", link.getTitle());
        data.put("partNo", link.getPartNo());
        data.put("allowDownload", link.getAllowDownload());
        data.put("expireAt", link.getExpireAt());

        if (link.getModel3dId() != null) {
            Model3D m = model3DMapper.selectById(link.getModel3dId());
            if (m != null) {
                Long fileId = m.getExtranetFileId() != null ? m.getExtranetFileId() : m.getIntranetFileId();
                data.put("modelUrl", "/api/share/public/" + token + "/file/" + fileId);
                data.put("modelName", m.getModelName());
                data.put("modelType", m.getModelType().name());
            }
        }
        return Result.success(data);
    }

    @Operation(summary = "公开文件下载(仅脱敏外网文件,需有效分享令牌)")
    @GetMapping("/public/{token}/file/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> publicFile(
            @PathVariable String token, @PathVariable Long fileId, HttpServletRequest request) {
        ShareLink link = validateShare(token, request);
        if (link.getModel3dId() == null) {
            throw new BusinessException("分享链接未关联3D模型");
        }
        Model3D m = model3DMapper.selectById(link.getModel3dId());
        if (m == null) {
            throw new BusinessException("模型不存在");
        }
        Long allowedId = m.getExtranetFileId() != null ? m.getExtranetFileId() : m.getIntranetFileId();
        if (!fileId.equals(allowedId)) {
            throw new BusinessException("无权访问该文件(仅限脱敏外观模型)");
        }
        org.springframework.core.io.Resource resource = fileService.download(fileId, false);
        PlmFile f = fileService.getById(fileId);
        String encoded = java.net.URLEncoder.encode(f.getFileName(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private ShareLink validateShare(String token, HttpServletRequest request) {
        ShareLink link = shareLinkMapper.selectOne(
                new LambdaQueryWrapper<ShareLink>().eq(ShareLink::getShareToken, token));
        if (link == null) {
            throw new BusinessException("分享链接不存在");
        }
        if (link.getStatus() == 0) {
            throw new BusinessException("分享链接已失效");
        }
        if (link.getExpireAt().isBefore(LocalDateTime.now())) {
            link.setStatus(0);
            shareLinkMapper.updateById(link);
            throw new BusinessException("分享链接已过期");
        }
        if (link.getMaxViews() != null && link.getViewCount() != null && link.getViewCount() >= link.getMaxViews()) {
            throw new BusinessException("访问次数已达上限");
        }
        ShareAccessLog log = new ShareAccessLog();
        log.setShareId(link.getId());
        log.setVisitorIp(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setReferer(request.getHeader("Referer"));
        log.setAccessedAt(LocalDateTime.now());
        accessLogMapper.insert(log);
        link.setViewCount((link.getViewCount() == null ? 0 : link.getViewCount()) + 1);
        shareLinkMapper.updateById(link);
        return link;
    }

    @Operation(summary = "查看访问日志")
    @GetMapping("/{id}/logs")
    public Result<List<ShareAccessLog>> logs(@PathVariable Long id) {
        return Result.success(accessLogMapper.selectList(
                new LambdaQueryWrapper<ShareAccessLog>()
                        .eq(ShareAccessLog::getShareId, id)
                        .orderByDesc(ShareAccessLog::getAccessedAt)));
    }
}
