package com.hjgd.plm.mold.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.mold.entity.InjectionSop;
import com.hjgd.plm.mold.mapper.InjectionSopMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "注塑成型SOP工艺卡")
@RestController
@RequestMapping("/injection-sop")
@RequiredArgsConstructor
public class InjectionSopController {

    private final InjectionSopMapper mapper;

    @Operation(summary = "按料号查询工艺卡")
    @GetMapping("/list")
    public Result<List<InjectionSop>> list(@RequestParam(required = false) String partNo,
                                           @RequestParam(required = false) String moldNo) {
        LambdaQueryWrapper<InjectionSop> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(partNo), InjectionSop::getPartNo, partNo)
                .eq(StringUtils.hasText(moldNo), InjectionSop::getMoldNo, moldNo)
                .orderByDesc(InjectionSop::getCreatedAt);
        return Result.success(mapper.selectList(w));
    }

    @Operation(summary = "新增/保存工艺卡")
    @OperationLog(value = "保存注塑SOP", partNo = "#sop.partNo")
    @PostMapping
    public Result<InjectionSop> save(@RequestBody InjectionSop sop) {
        if (sop.getId() == null) {
            sop.setStatus("DRAFT");
            sop.setVersionNo("V1.0");
            sop.setCreatedBy(SecurityUtils.getCurrentRealName());
            mapper.insert(sop);
        } else {
            mapper.updateById(sop);
        }
        return Result.success(sop);
    }

    @Operation(summary = "生效工艺卡(可批量打印下发车间)")
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        InjectionSop sop = mapper.selectById(id);
        sop.setStatus("RELEASED");
        mapper.updateById(sop);
        return Result.success();
    }
}
