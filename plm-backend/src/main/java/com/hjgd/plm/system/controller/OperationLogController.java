package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysOperationLog;
import com.hjgd.plm.system.mapper.SysOperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final SysOperationLogMapper logMapper;

    @Operation(summary = "操作日志分页")
    @GetMapping("/page")
    public Result<PageResult<SysOperationLog>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String partNo) {
        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(operator), SysOperationLog::getOperator, operator)
                .like(StringUtils.hasText(operation), SysOperationLog::getOperation, operation)
                .eq(StringUtils.hasText(partNo), SysOperationLog::getPartNo, partNo)
                .orderByDesc(SysOperationLog::getCreatedAt);
        Page<SysOperationLog> page = logMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }
}
