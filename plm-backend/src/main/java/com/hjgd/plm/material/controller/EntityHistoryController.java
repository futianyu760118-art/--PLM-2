package com.hjgd.plm.material.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.entity.EntityHistory;
import com.hjgd.plm.material.service.EntityHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用实体变更历史(差异比对 + 回滚)。
 * 企业微信式回看:每条记录展示字段级 before/after 差异,可还原任意版本快照。
 */
@Tag(name = "V1 实体历史")
@RestController
@RequestMapping("/v1/history")
@RequiredArgsConstructor
public class EntityHistoryController {

    private final EntityHistoryService service;

    @Operation(summary = "查询对象变更历史(差异逐字段)")
    @GetMapping("/{objectType}/{objectId}")
    public Result<List<EntityHistory>> history(@PathVariable String objectType,
                                              @PathVariable String objectId) {
        return Result.success(service.history(objectType, objectId));
    }

    @Operation(summary = "查询指定版本的完整快照(支持回滚)")
    @GetMapping("/{objectType}/{objectId}/snapshot")
    public Result<String> snapshot(@PathVariable String objectType,
                                    @PathVariable String objectId,
                                    @RequestParam int version) {
        return Result.success(service.snapshotAt(objectType, objectId, version));
    }
}