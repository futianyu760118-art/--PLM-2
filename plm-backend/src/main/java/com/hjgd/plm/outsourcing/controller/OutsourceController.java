package com.hjgd.plm.outsourcing.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.outsourcing.dto.OutsourceRequestDTO;
import com.hjgd.plm.outsourcing.entity.OutsourceFile;
import com.hjgd.plm.outsourcing.entity.OutsourceRequest;
import com.hjgd.plm.outsourcing.service.OutsourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "外协发图审批")
@RestController
@RequestMapping("/outsourcing")
@RequiredArgsConstructor
public class OutsourceController {

    private final OutsourceService outsourceService;

    @Operation(summary = "外协申请分页")
    @GetMapping("/page")
    public Result<PageResult<OutsourceRequest>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String outsourceCompany,
            @RequestParam(required = false) String status) {
        return Result.success(outsourceService.page(pageNum, pageSize, outsourceCompany, status));
    }

    @Operation(summary = "申请详情")
    @GetMapping("/{id}")
    public Result<OutsourceRequest> get(@PathVariable Long id) {
        return Result.success(outsourceService.getById(id));
    }

    @Operation(summary = "新建外协申请")
    @PreAuthorize("hasAuthority('outsourcing:add')")
    @PostMapping
    public Result<OutsourceRequest> create(@Valid @RequestBody OutsourceRequestDTO dto) {
        return Result.success(outsourceService.create(dto));
    }

    @Operation(summary = "提交审批")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        outsourceService.submit(id);
        return Result.success();
    }

    @Operation(summary = "审批通过(自动生成水印+有效期)")
    @PreAuthorize("hasAuthority('outsourcing:approve')")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestParam String comment) {
        outsourceService.approve(id, comment);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PreAuthorize("hasAuthority('outsourcing:approve')")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam String comment) {
        outsourceService.reject(id, comment);
        return Result.success();
    }

    @Operation(summary = "查看外协文件包")
    @GetMapping("/{id}/files")
    public Result<List<OutsourceFile>> files(@PathVariable Long id) {
        return Result.success(outsourceService.getFiles(id));
    }

    @Operation(summary = "外协下载(留痕+时效校验)")
    @OperationLog(value = "外协文件下载")
    @PreAuthorize("hasAuthority('outsourcing:download')")
    @PostMapping("/{id}/download/{fileId}")
    public Result<Void> download(@PathVariable Long id, @PathVariable Long fileId) {
        outsourceService.download(id, fileId);
        return Result.success();
    }
}
