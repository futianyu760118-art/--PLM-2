package com.hjgd.plm.rd.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.rd.service.ChangeLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 项目变更列表。
 */
@Tag(name = "研发自治中心-变更列表")
@RestController
@RequestMapping({"/v1/rd/changes", "/rd/changes"})
@RequiredArgsConstructor
public class RdChangeController {

    private final ChangeLogService changeLogService;

    @Operation(summary = "变更列表")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String projectNo,
                                            @RequestParam(required = false) String objectType,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(changeLogService.list(projectNo, objectType, pageNum, pageSize));
    }
}
