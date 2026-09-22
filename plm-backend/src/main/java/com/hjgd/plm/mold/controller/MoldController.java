package com.hjgd.plm.mold.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.mold.entity.Mold;
import com.hjgd.plm.mold.entity.MoldTrialLog;
import com.hjgd.plm.mold.enums.MoldStatus;
import com.hjgd.plm.mold.mapper.MoldMapper;
import com.hjgd.plm.mold.mapper.MoldTrialLogMapper;
import com.hjgd.plm.system.service.SequenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "模具资产管理")
@RestController
@RequestMapping("/mold")
@RequiredArgsConstructor
public class MoldController {

    private final MoldMapper moldMapper;
    private final MoldTrialLogMapper trialLogMapper;
    private final SequenceService sequenceService;

    @Operation(summary = "模具台账分页")
    @GetMapping("/page")
    public Result<PageResult<Mold>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String moldNo,
            @RequestParam(required = false) String partNo,
            @RequestParam(required = false) MoldStatus status) {
        LambdaQueryWrapper<Mold> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(moldNo), Mold::getMoldNo, moldNo)
                .like(StringUtils.hasText(partNo), Mold::getPartNo, partNo)
                .eq(status != null, Mold::getStatus, status)
                .orderByDesc(Mold::getCreatedAt);
        Page<Mold> page = moldMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "模具详情")
    @GetMapping("/{id}")
    public Result<Mold> get(@PathVariable Long id) {
        return Result.success(moldMapper.selectById(id));
    }

    @Operation(summary = "新增模具台账")
    @OperationLog(value = "新增模具台账", partNo = "#mold.partNo")
    @PreAuthorize("hasAuthority('mold:add')")
    @PostMapping
    public Result<Mold> create(@Valid @RequestBody Mold mold) {
        mold.setMoldNo(sequenceService.nextNo("MOLD_NO"));
        if (mold.getStatus() == null) {
            mold.setStatus(MoldStatus.IN_DESIGN);
        }
        if (mold.getAccumulateShots() == null) {
            mold.setAccumulateShots(0L);
        }
        mold.setCreatedBy(SecurityUtils.getCurrentRealName());
        moldMapper.insert(mold);
        return Result.success(mold);
    }

    @Operation(summary = "修改模具台账")
    @PreAuthorize("hasAuthority('mold:edit')")
    @PutMapping
    public Result<Mold> update(@RequestBody Mold mold) {
        moldMapper.updateById(mold);
        return Result.success(mold);
    }

    @Operation(summary = "更新生产啤数")
    @PreAuthorize("hasAuthority('mold:edit')")
    @PutMapping("/{id}/shots")
    public Result<Void> updateShots(@PathVariable Long id, @RequestParam Long shots) {
        Mold mold = moldMapper.selectById(id);
        mold.setAccumulateShots(shots);
        moldMapper.updateById(mold);
        return Result.success();
    }

    @Operation(summary = "模具报废")
    @PreAuthorize("hasAuthority('mold:edit')")
    @PutMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id) {
        Mold mold = moldMapper.selectById(id);
        mold.setStatus(MoldStatus.OBSOLETE);
        mold.setScrapDate(java.time.LocalDate.now());
        moldMapper.updateById(mold);
        return Result.success();
    }

    @Operation(summary = "试模履历列表")
    @GetMapping("/{moldNo}/trials")
    public Result<List<MoldTrialLog>> trials(@PathVariable String moldNo) {
        return Result.success(trialLogMapper.selectList(
                new LambdaQueryWrapper<MoldTrialLog>()
                        .eq(MoldTrialLog::getMoldNo, moldNo)
                        .orderByDesc(MoldTrialLog::getTrialTime)));
    }

    @Operation(summary = "新增试模/修模记录")
    @OperationLog(value = "试模修模记录")
    @PreAuthorize("hasAuthority('mold:trial')")
    @PostMapping("/trial")
    public Result<MoldTrialLog> addTrial(@RequestBody MoldTrialLog log) {
        log.setTrialTime(log.getTrialTime() == null ? LocalDateTime.now() : log.getTrialTime());
        log.setHandler(SecurityUtils.getCurrentRealName());
        trialLogMapper.insert(log);
        return Result.success(log);
    }
}
