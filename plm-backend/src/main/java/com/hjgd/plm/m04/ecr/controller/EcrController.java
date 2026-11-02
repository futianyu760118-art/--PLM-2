package com.hjgd.plm.m04.ecr.controller;

import com.hjgd.plm.m04.approval.dto.ApprovalActionDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.m04.ecr.dto.EcrDTO;
import com.hjgd.plm.m04.ecr.dto.EcrQueryDTO;
import com.hjgd.plm.m04.ecr.entity.Ecr;
import com.hjgd.plm.m04.ecr.service.EcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ECR工程变更申请")
@RestController
@RequestMapping("/ecr")
@RequiredArgsConstructor
public class EcrController {

    private final EcrService ecrService;

    @Operation(summary = "ECR分页查询")
    @GetMapping("/page")
    public Result<PageResult<Ecr>> page(EcrQueryDTO query) {
        return Result.success(ecrService.page(query));
    }

    @Operation(summary = "ECR详情")
    @GetMapping("/{id}")
    public Result<Ecr> get(@PathVariable Long id) {
        return Result.success(ecrService.getById(id));
    }

    @Operation(summary = "新建ECR")
    @PreAuthorize("hasAuthority('ecr:add')")
    @PostMapping
    public Result<Ecr> create(@Valid @RequestBody EcrDTO dto) {
        return Result.success(ecrService.create(dto));
    }

    @Operation(summary = "修改ECR")
    @PreAuthorize("hasAuthority('ecr:edit')")
    @PutMapping
    public Result<Ecr> update(@Valid @RequestBody EcrDTO dto) {
        return Result.success(ecrService.update(dto));
    }

    @Operation(summary = "删除ECR")
    @PreAuthorize("hasAuthority('ecr:edit')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ecrService.delete(id);
        return Result.success();
    }

    @Operation(summary = "提交审批")
    @PreAuthorize("hasAuthority('ecr:edit')")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        ecrService.submit(id);
        return Result.success();
    }

    @Operation(summary = "审批动作")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody ApprovalActionDTO dto) {
        ecrService.approve(id, dto.getAction(), dto.getComment());
        return Result.success();
    }

    @Operation(summary = "ECR审批进度")
    @GetMapping("/{id}/progress")
    public Result<ApprovalProgressVO> progress(@PathVariable Long id) {
        return Result.success(ecrService.progress(id));
    }

    @Operation(summary = "转ECN")
    @PreAuthorize("hasAuthority('ecr:edit')")
    @PostMapping("/{id}/to-ecn")
    public Result<Ecr> toEcn(@PathVariable Long id) {
        return Result.success(ecrService.toEcn(id));
    }

    @Operation(summary = "关闭ECR")
    @PreAuthorize("hasAuthority('ecr:edit')")
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        ecrService.close(id);
        return Result.success();
    }
}
