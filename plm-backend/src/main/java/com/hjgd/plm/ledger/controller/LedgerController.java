package com.hjgd.plm.ledger.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.ledger.dto.BatchResultVO;
import com.hjgd.plm.ledger.dto.LedgerDetailVO;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.dto.LedgerRefVO;
import com.hjgd.plm.ledger.dto.LedgerRequest;
import com.hjgd.plm.ledger.dto.LinkRequest;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.entity.PlmLedgerAttachment;
import com.hjgd.plm.ledger.mapper.PlmLedgerAttachmentMapper;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerObjectResolver;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.ledger.service.LedgerSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 证据台账（能力线 A）。
 *
 * <p>台账录入页与资料汇总处共用本控制器的列表/详情：两侧是同一行数据的两个视图
 * （AC-A3-3 逐字段一致由数据模型保证，无需同步任务）。
 *
 * <p>权限：查看 ledger:view、新建 ledger:add、编辑 ledger:edit、删除 ledger:delete、
 * 下载 ledger:download；行级范围在服务层按部门/本人收窄（AC-A6-1 ~ A6-3）。
 */
@Tag(name = "证据台账")
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerSyncService ledgerSyncService;
    private final LedgerAuditService auditService;
    private final FileService fileService;
    private final PlmLedgerAttachmentMapper attachmentMapper;
    private final PlmLedgerMapper ledgerMapper;

    // ---------- 查询 ----------

    @Operation(summary = "台账/资料汇总处 组合筛选分页（AC-A5-1~A5-5）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ledger:view')")
    public Result<PageResult<PlmLedger>> list(LedgerQuery query) {
        return Result.success(ledgerService.page(query));
    }

    @Operation(summary = "证据详情（含附件与关联）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:view')")
    public Result<LedgerDetailVO> detail(@PathVariable Long id) {
        return Result.success(ledgerService.detail(id));
    }

    @Operation(summary = "模块/表单侧证据区：按关联对象取引用条目（AC-A3-1/A3-5）")
    @GetMapping("/by-object")
    @PreAuthorize("hasAuthority('ledger:view')")
    public Result<List<LedgerRefVO>> byObject(@RequestParam String objectType,
                                             @RequestParam String objectId) {
        return Result.success(ledgerService.byObject(objectType, objectId));
    }

    @Operation(summary = "录入表单下拉项与边界值")
    @GetMapping("/options")
    public Result<Map<String, Object>> options() {
        return Result.success(ledgerService.formOptions());
    }

    @Operation(summary = "关联对象检索（前端下拉）")
    @GetMapping("/objects")
    public Result<List<LedgerObjectResolver.ObjectRef>> objects(@RequestParam String objectType,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "20") int limit) {
        return Result.success(ledgerService.searchObjects(objectType, keyword, limit));
    }

    @Operation(summary = "按证据编号查看完整变更轨迹（AC-A6-7）")
    @GetMapping("/trail/{ledgerNo}")
    @PreAuthorize("hasAuthority('ledger:view')")
    public Result<Map<String, Object>> trail(@PathVariable String ledgerNo) {
        return Result.success(ledgerService.trail(ledgerNo));
    }

    @Operation(summary = "责任人候选（AC-A2-2，供非管理员录入人选择）")
    @GetMapping("/owners")
    public Result<List<Map<String, Object>>> owners(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return Result.success(ledgerService.searchOwners(keyword, limit));
    }

    // ---------- 录入 ----------

    @Operation(summary = "新建证据（单界面提交，AC-A1/A2）")
    @PostMapping("")
    @PreAuthorize("hasAuthority('ledger:add')")
    public Result<LedgerDetailVO> create(@RequestBody LedgerRequest request) {
        return Result.success(ledgerService.create(request));
    }

    @Operation(summary = "编辑证据")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<LedgerDetailVO> update(@PathVariable Long id, @RequestBody LedgerRequest request) {
        return Result.success(ledgerService.update(id, request));
    }

    @Operation(summary = "批量上传：N 个文件生成 1 条台账（AC-A5-6）")
    @PostMapping("/batch-upload")
    @PreAuthorize("hasAuthority('ledger:add')")
    public Result<BatchResultVO> batchUpload(@RequestPart("files") MultipartFile[] files,
                                             @RequestPart("meta") LedgerRequest meta) {
        return Result.success(ledgerService.batchUpload(meta, files));
    }

    @Operation(summary = "删除证据")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        ledgerService.delete(id);
        return Result.success();
    }

    // ---------- 附件 ----------

    @Operation(summary = "追加附件")
    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<LedgerDetailVO> addAttachments(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(ledgerService.addAttachments(id, longList(body.get("fileIds"))));
    }

    @Operation(summary = "移除附件（保留台账本体）")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<Void> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        ledgerService.removeAttachment(id, attachmentId);
        return Result.success();
    }

    @Operation(summary = "下载附件（记入操作日志，AC-A6-4）")
    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('ledger:download')")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        PlmLedgerAttachment attachment = requireAttachment(attachmentId);
        ledgerService.requireReadable(attachment.getLedgerId());
        Resource resource = fileService.download(attachment.getFileId(), false);
        auditService.log("DOWNLOAD", ledgerNoOf(attachment.getLedgerId()), null,
                Map.of("fileName", String.valueOf(attachment.getFileName())));
        return fileResponse(attachment, resource, true);
    }

    @Operation(summary = "在线预览附件")
    @GetMapping("/attachments/{attachmentId}/preview")
    @PreAuthorize("hasAuthority('ledger:view')")
    public ResponseEntity<Resource> preview(@PathVariable Long attachmentId) {
        PlmLedgerAttachment attachment = requireAttachment(attachmentId);
        ledgerService.requireReadable(attachment.getLedgerId());
        Resource resource = fileService.download(attachment.getFileId(), false);
        return fileResponse(attachment, resource, false);
    }

    // ---------- 关联 ----------

    @Operation(summary = "新增关联")
    @PostMapping("/{id}/links")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<LedgerDetailVO> addLink(@PathVariable Long id, @RequestBody LinkRequest request) {
        return Result.success(ledgerService.addLink(id, request));
    }

    @Operation(summary = "解除关联（仅解除引用，AC-A4-6）")
    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<Void> removeLink(@PathVariable Long id, @PathVariable Long linkId) {
        ledgerService.removeLink(id, linkId);
        return Result.success();
    }

    @Operation(summary = "批量关联：N 条证据关联到 1 个对象（AC-A5-7）")
    @PostMapping("/batch-link")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<BatchResultVO> batchLink(@RequestBody Map<String, Object> body) {
        LinkRequest link = new LinkRequest();
        link.setObjectType(str(body.get("objectType")));
        link.setObjectId(str(body.get("objectId")));
        return Result.success(ledgerService.batchLink(longList(body.get("ledgerIds")), link));
    }

    // ---------- 模块侧回写（AC-A3） ----------

    @Operation(summary = "模块/表单侧回写台账字段（双向同步，LWW + 通知被覆盖方）")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<LedgerSyncService.SyncResult> sync(@RequestBody Map<String, Object> body) {
        String objectType = str(body.get("objectType"));
        String objectId = str(body.get("objectId"));
        String source = str(body.get("sourceSystem"));
        Object raw = body.get("changes");
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "changes 必须为对象");
        }
        Map<String, Object> changes = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            changes.put(String.valueOf(e.getKey()), e.getValue());
        }
        return Result.success(ledgerSyncService.applyFromModule(objectType, objectId, changes, source));
    }

    // ---------- 内部 ----------

    private PlmLedgerAttachment requireAttachment(Long attachmentId) {
        PlmLedgerAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    private String ledgerNoOf(Long ledgerId) {
        PlmLedger ledger = ledgerMapper.selectById(ledgerId);
        return ledger == null ? String.valueOf(ledgerId) : ledger.getLedgerNo();
    }

    private ResponseEntity<Resource> fileResponse(PlmLedgerAttachment attachment, Resource resource,
                                                 boolean asAttachment) {
        String name = attachment.getFileName() == null ? "attachment" : attachment.getFileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        String disposition = (asAttachment ? "attachment" : "inline") + "; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(asAttachment ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType(attachment.getFileExt())))
                .body(resource);
    }

    private String contentType(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return switch (e) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".txt", ".md", ".csv", ".json", ".xml" -> "text/plain; charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<Long> longList(Object raw) {
        List<Long> out = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                String s = String.valueOf(o).trim();
                if (s.isEmpty()) {
                    continue;
                }
                try {
                    out.add(Long.valueOf(s));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "非法的 ID: " + s);
                }
            }
        }
        return out;
    }
}
