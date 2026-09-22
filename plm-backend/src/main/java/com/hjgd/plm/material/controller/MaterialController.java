package com.hjgd.plm.material.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.dto.MaterialQueryDTO;
import com.hjgd.plm.material.dto.MaterialSupplierDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.entity.MaterialVersion;
import com.hjgd.plm.material.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物料/零件主数据")
@RestController
@RequestMapping({"/material", "/v1/parts"})
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<Material>> page(MaterialQueryDTO query) {
        return Result.success(materialService.page(query));
    }

    @Operation(summary = "V1列表(同分页)")
    @GetMapping(params = "pageNum")
    public Result<PageResult<Material>> pageV1(MaterialQueryDTO query) {
        return Result.success(materialService.page(query));
    }

    @Operation(summary = "详情 by id")
    @GetMapping("/{id}")
    public Result<Material> get(@PathVariable Long id) {
        return Result.success(materialService.getById(id));
    }

    @Operation(summary = "详情 by partNo")
    @GetMapping("/partNo/{partNo}")
    public Result<Material> getByPartNo(@PathVariable String partNo) {
        return Result.success(materialService.getByPartNo(partNo));
    }

    @Operation(summary = "新建(自动编号)")
    @PreAuthorize("hasAuthority('material:add')")
    @PostMapping
    public Result<Material> create(@Valid @RequestBody MaterialDTO dto) {
        return Result.success(materialService.create(dto));
    }

    @Operation(summary = "修改(已发布锁定)")
    @PreAuthorize("hasAuthority('material:edit')")
    @PutMapping
    public Result<Material> update(@Valid @RequestBody MaterialDTO dto) {
        return Result.success(materialService.update(dto));
    }

    @Operation(summary = "删除草稿")
    @PreAuthorize("hasAuthority('material:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.success();
    }

    @Operation(summary = "提交评审")
    @OperationLog(value = "提交评审", partNo = "#id")
    @PutMapping("/{id}/review")
    public Result<Void> submitReviewLegacy(@PathVariable Long id) {
        materialService.submitReview(id);
        return Result.success();
    }

    @PostMapping("/{id}/actions/submit-review")
    @OperationLog(value = "提交评审", partNo = "#id")
    public Result<Void> submitReview(@PathVariable Long id) {
        materialService.submitReview(id);
        return Result.success();
    }

    @Operation(summary = "正式发布")
    @OperationLog(value = "正式发布", partNo = "#id")
    @PreAuthorize("hasAuthority('material:release')")
    @PutMapping("/{id}/release")
    public Result<Void> releaseLegacy(@PathVariable Long id) {
        materialService.release(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('material:release')")
    @PostMapping("/{id}/actions/release")
    @OperationLog(value = "正式发布", partNo = "#id")
    public Result<Void> release(@PathVariable Long id) {
        materialService.release(id);
        return Result.success();
    }

    @Operation(summary = "转量产")
    @OperationLog(value = "转量产", partNo = "#id")
    @PreAuthorize("hasAuthority('material:release')")
    @PostMapping("/{id}/actions/to-production")
    public Result<Void> toProduction(@PathVariable Long id) {
        materialService.toProduction(id);
        return Result.success();
    }

    @Operation(summary = "作废")
    @OperationLog(value = "作废", partNo = "#id")
    @PreAuthorize("hasAuthority('material:release')")
    @PostMapping("/{id}/actions/obsolete")
    public Result<Void> obsolete(@PathVariable Long id) {
        materialService.obsolete(id);
        return Result.success();
    }

    @Operation(summary = "封存")
    @OperationLog(value = "封存", partNo = "#id")
    @PreAuthorize("hasAuthority('material:release')")
    @PostMapping("/{id}/actions/seal")
    public Result<Void> seal(@PathVariable Long id) {
        materialService.seal(id);
        return Result.success();
    }

    @Operation(summary = "数据质量检查")
    @PostMapping("/{id}/actions/dq-check")
    public Result<DqRunResult> dqCheck(@PathVariable Long id) {
        return Result.success(materialService.checkQuality(id));
    }

    @GetMapping("/{id}/score")
    public Result<DqRunResult> score(@PathVariable Long id) {
        return Result.success(materialService.checkQuality(id));
    }

    @Operation(summary = "版本历史")
    @GetMapping("/{id}/versions")
    public Result<List<MaterialVersion>> versions(@PathVariable Long id) {
        return Result.success(materialService.listVersions(id));
    }

    @Operation(summary = "校验料号重复")
    @GetMapping("/check/{partNo}")
    public Result<Boolean> checkPartNo(@PathVariable String partNo,
                                       @RequestParam(required = false) Long excludeId) {
        return Result.success(materialService.checkPartNoDuplicate(partNo, excludeId));
    }

    @Operation(summary = "查询物料的多阶梯供应商(主供/备选/试产)")
    @GetMapping("/{id}/suppliers")
    public Result<List<MaterialSupplierDTO>> suppliers(@PathVariable Long id) {
        return Result.success(materialService.listSuppliers(id));
    }
}
