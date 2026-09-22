package com.hjgd.plm.quality.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.quality.dto.InspectionStandardDTO;
import com.hjgd.plm.quality.entity.InspectionStandard;
import com.hjgd.plm.quality.enums.InspectionCategory;
import com.hjgd.plm.quality.mapper.InspectionStandardMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "IQC/IPQC品质检验标准")
@RestController
@RequestMapping("/quality")
@RequiredArgsConstructor
public class QualityController {

    private final InspectionStandardMapper mapper;

    @Operation(summary = "检验标准分页")
    @GetMapping("/page")
    public Result<PageResult<InspectionStandard>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String partNo,
            @RequestParam(required = false) InspectionCategory category,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InspectionStandard> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(partNo), InspectionStandard::getPartNo, partNo)
                .eq(category != null, InspectionStandard::getCategory, category)
                .eq(StringUtils.hasText(status), InspectionStandard::getStatus, status)
                .orderByDesc(InspectionStandard::getCreatedAt);
        Page<InspectionStandard> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "新增检验标准")
    @OperationLog(value = "新增检验标准", partNo = "#dto.partNo")
    @PreAuthorize("hasAuthority('quality:add')")
    @PostMapping
    public Result<InspectionStandard> create(@Valid @RequestBody InspectionStandardDTO dto) {
        InspectionStandard entity = new InspectionStandard();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus("DRAFT");
        entity.setVersionNo("V1.0");
        entity.setCreatedBy(SecurityUtils.getCurrentRealName());
        mapper.insert(entity);
        return Result.success(entity);
    }

    @Operation(summary = "修改检验标准")
    @OperationLog(value = "修改检验标准", partNo = "#dto.partNo")
    @PreAuthorize("hasAuthority('quality:edit')")
    @PutMapping
    public Result<InspectionStandard> update(@Valid @RequestBody InspectionStandardDTO dto) {
        InspectionStandard exist = mapper.selectById(dto.getId());
        if (exist == null) {
            return Result.failed("检验标准不存在");
        }
        BeanUtils.copyProperties(dto, exist, "id", "partNo", "status", "versionNo", "createdBy", "createdAt");
        mapper.updateById(exist);
        return Result.success(exist);
    }

    @Operation(summary = "删除检验标准")
    @PreAuthorize("hasAuthority('quality:add')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "生效检验标准(图纸版本更新时提示同步)")
    @PreAuthorize("hasAuthority('quality:release')")
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        InspectionStandard exist = mapper.selectById(id);
        exist.setStatus("RELEASED");
        mapper.updateById(exist);
        return Result.success();
    }

    @Operation(summary = "作废(灰化封存)")
    @PutMapping("/{id}/obsolete")
    public Result<Void> obsolete(@PathVariable Long id) {
        InspectionStandard exist = mapper.selectById(id);
        exist.setStatus("OBSOLETE");
        mapper.updateById(exist);
        return Result.success();
    }
}
