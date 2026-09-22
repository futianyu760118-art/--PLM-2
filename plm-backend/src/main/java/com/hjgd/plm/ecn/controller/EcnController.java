package com.hjgd.plm.ecn.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.ecn.dto.EcnDTO;
import com.hjgd.plm.ecn.dto.EcnQueryDTO;
import com.hjgd.plm.ecn.dto.EcnReviewDTO;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.entity.EcnFlowLog;
import com.hjgd.plm.ecn.service.EcnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ECN工程变更")
@RestController
@RequestMapping({"/ecn", "/v1/ecns"})
@RequiredArgsConstructor
public class EcnController {

    private final EcnService ecnService;

    @Operation(summary = "ECN分页查询")
    @GetMapping("/page")
    public Result<PageResult<Ecn>> page(EcnQueryDTO query) {
        return Result.success(ecnService.page(query));
    }

    @Operation(summary = "ECN详情")
    @GetMapping("/{id}")
    public Result<Ecn> get(@PathVariable Long id) {
        return Result.success(ecnService.getById(id));
    }

    @Operation(summary = "新建ECN")
    @PreAuthorize("hasAuthority('ecn:add')")
    @PostMapping
    public Result<Ecn> create(@Valid @RequestBody EcnDTO dto) {
        return Result.success(ecnService.create(dto));
    }

    @Operation(summary = "修改ECN(仅草稿/驳回状态)")
    @PreAuthorize("hasAuthority('ecn:add')")
    @PutMapping
    public Result<Ecn> update(@Valid @RequestBody EcnDTO dto) {
        return Result.success(ecnService.update(dto));
    }

    @Operation(summary = "删除ECN(仅草稿)")
    @PreAuthorize("hasAuthority('ecn:add')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ecnService.delete(id);
        return Result.success();
    }

    @Operation(summary = "提交审批")
    @PreAuthorize("hasAuthority('ecn:add')")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        ecnService.submit(id);
        return Result.success();
    }

    @Operation(summary = "一审通过(研发主管)")
    @PreAuthorize("hasAuthority('ecn:review1')")
    @PutMapping("/{id}/l1/approve")
    public Result<Void> l1Approve(@PathVariable Long id, @Valid @RequestBody EcnReviewDTO dto) {
        ecnService.reviewL1Approve(id, dto);
        return Result.success();
    }

    @Operation(summary = "一审驳回(研发主管)")
    @PreAuthorize("hasAuthority('ecn:review1')")
    @PutMapping("/{id}/l1/reject")
    public Result<Void> l1Reject(@PathVariable Long id, @Valid @RequestBody EcnReviewDTO dto) {
        ecnService.reviewL1Reject(id, dto);
        return Result.success();
    }

    @Operation(summary = "二审通过(供应链总监)")
    @PreAuthorize("hasAuthority('ecn:review2')")
    @PutMapping("/{id}/l2/approve")
    public Result<Void> l2Approve(@PathVariable Long id, @Valid @RequestBody EcnReviewDTO dto) {
        ecnService.reviewL2Approve(id, dto);
        return Result.success();
    }

    @Operation(summary = "二审驳回(供应链总监)")
    @PreAuthorize("hasAuthority('ecn:review2')")
    @PutMapping("/{id}/l2/reject")
    public Result<Void> l2Reject(@PathVariable Long id, @Valid @RequestBody EcnReviewDTO dto) {
        ecnService.reviewL2Reject(id, dto);
        return Result.success();
    }

    @Operation(summary = "ECN生效(版本自动升级)")
    @PreAuthorize("hasAuthority('ecn:effect')")
    @PutMapping("/{id}/effect")
    public Result<Void> effect(@PathVariable Long id) {
        ecnService.effect(id);
        return Result.success();
    }

    @Operation(summary = "作废ECN")
    @PutMapping("/{id}/void")
    public Result<Void> voidEcn(@PathVariable Long id) {
        ecnService.voidEcn(id);
        return Result.success();
    }

    @Operation(summary = "ECN审批流转记录")
    @GetMapping("/{id}/logs")
    public Result<List<EcnFlowLog>> flowLogs(@PathVariable Long id) {
        return Result.success(ecnService.getFlowLogs(id));
    }
}
