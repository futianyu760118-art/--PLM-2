package com.hjgd.plm.m04.document.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.m04.document.dto.DocumentAclDTO;
import com.hjgd.plm.m04.document.dto.DocumentDTO;
import com.hjgd.plm.m04.document.dto.DocumentQueryDTO;
import com.hjgd.plm.m04.document.dto.DocumentStateDTO;
import com.hjgd.plm.m04.document.dto.DocumentVersionDTO;
import com.hjgd.plm.m04.document.entity.PlmDocument;
import com.hjgd.plm.m04.document.entity.PlmDocumentAcl;
import com.hjgd.plm.m04.document.entity.PlmDocumentVersion;
import com.hjgd.plm.m04.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文档中心（T05–T07）。
 *
 * <p>五区流转、版本链、检入检出、对象级权限都通过这里暴露；
 * 老 PLM 的"文档版本"页面对应 {@code /versions} 与 {@code /versions/current}。
 */
@Tag(name = "文档中心")
@RestController
@RequestMapping({"/document", "/v1/documents"})
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<PlmDocument>> page(DocumentQueryDTO query) {
        return Result.success(documentService.page(query));
    }

    @Operation(summary = "五区数量统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(documentService.stats());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<PlmDocument> get(@PathVariable Long id) {
        return Result.success(documentService.getById(id));
    }

    @Operation(summary = "按料号查文档")
    @GetMapping("/part/{partNo}")
    public Result<List<PlmDocument>> listByPart(@PathVariable String partNo) {
        return Result.success(documentService.listByPart(partNo));
    }

    @Operation(summary = "按项目查文档")
    @GetMapping("/project/{projectNo}")
    public Result<List<PlmDocument>> listByProject(@PathVariable String projectNo) {
        return Result.success(documentService.listByProject(projectNo));
    }

    @Operation(summary = "新建（自动编号）")
    @PreAuthorize("hasAuthority('document:add')")
    @PostMapping
    public Result<PlmDocument> create(@Valid @RequestBody DocumentDTO dto) {
        return Result.success(documentService.create(dto));
    }

    @Operation(summary = "修改元数据")
    @PreAuthorize("hasAuthority('document:edit')")
    @PutMapping
    public Result<PlmDocument> update(@Valid @RequestBody DocumentDTO dto) {
        return Result.success(documentService.update(dto));
    }

    @Operation(summary = "删除（仅工作区/回收区）")
    @PreAuthorize("hasAuthority('document:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "五区流转")
    @PreAuthorize("hasAnyAuthority('document:release','document:edit')")
    @PutMapping("/{id}/state")
    public Result<PlmDocument> changeState(@PathVariable Long id,
                                           @Valid @RequestBody DocumentStateDTO dto) {
        return Result.success(documentService.changeState(id, dto.getState(), dto.getRemark()));
    }

    // ---------------------------------------------------------------- 版本链

    @Operation(summary = "版本列表")
    @GetMapping("/{id}/versions")
    public Result<List<PlmDocumentVersion>> listVersions(@PathVariable Long id) {
        return Result.success(documentService.listVersions(id));
    }

    @Operation(summary = "当前生效版本")
    @GetMapping("/{id}/versions/current")
    public Result<PlmDocumentVersion> currentVersion(@PathVariable Long id) {
        return Result.success(documentService.currentVersion(id));
    }

    @Operation(summary = "新增版本（需先检出，ADMIN 可 force）")
    @PreAuthorize("hasAuthority('document:edit')")
    @PostMapping("/{id}/versions")
    public Result<PlmDocumentVersion> addVersion(@PathVariable Long id,
                                                 @Valid @RequestBody DocumentVersionDTO dto) {
        return Result.success(documentService.addVersion(id, dto));
    }

    @Operation(summary = "回退到历史版本（复制为新版本）")
    @PreAuthorize("hasAuthority('document:edit')")
    @PostMapping("/{id}/versions/{versionId}/restore")
    public Result<PlmDocumentVersion> restore(@PathVariable Long id,
                                              @PathVariable Long versionId,
                                              @RequestParam(required = false) String changeNote) {
        return Result.success(documentService.restoreVersion(id, versionId, changeNote));
    }

    // ---------------------------------------------------------------- 检入检出

    @Operation(summary = "检出（加锁）")
    @PreAuthorize("hasAnyAuthority('document:edit','document:checkout')")
    @PutMapping("/{id}/checkout")
    public Result<PlmDocument> checkout(@PathVariable Long id) {
        return Result.success(documentService.checkout(id));
    }

    @Operation(summary = "检入（上传新版本并解锁）")
    @PreAuthorize("hasAnyAuthority('document:edit','document:checkout')")
    @PutMapping("/{id}/checkin")
    public Result<PlmDocument> checkin(@PathVariable Long id,
                                       @Valid @RequestBody DocumentVersionDTO dto) {
        return Result.success(documentService.checkin(id, dto));
    }

    @Operation(summary = "取消检出（仅本人或管理员）")
    @PreAuthorize("hasAnyAuthority('document:edit','document:checkout')")
    @PutMapping("/{id}/checkout/cancel")
    public Result<PlmDocument> cancelCheckout(@PathVariable Long id) {
        return Result.success(documentService.cancelCheckout(id));
    }

    // ---------------------------------------------------------------- 权限

    @Operation(summary = "查看对象级权限")
    @GetMapping("/{id}/acl")
    public Result<List<PlmDocumentAcl>> listAcl(@PathVariable Long id) {
        return Result.success(documentService.listAcl(id));
    }

    @Operation(summary = "授予/更新对象级权限")
    @PreAuthorize("hasAnyAuthority('document:acl','document:edit')")
    @PostMapping("/{id}/acl")
    public Result<Void> grantAcl(@PathVariable Long id,
                                 @Valid @RequestBody List<DocumentAclDTO> acls) {
        documentService.grantAcl(id, acls);
        return Result.success();
    }

    @Operation(summary = "撤销对象级权限")
    @PreAuthorize("hasAnyAuthority('document:acl','document:edit')")
    @DeleteMapping("/{id}/acl/{aclId}")
    public Result<Void> revokeAcl(@PathVariable Long id, @PathVariable Long aclId) {
        documentService.revokeAcl(id, aclId);
        return Result.success();
    }
}
