package com.hjgd.plm.sync.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.sync.dto.InboundRequest;
import com.hjgd.plm.sync.entity.SyncConflict;
import com.hjgd.plm.sync.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M04 同步中枢 API。契约见 docs/m04-contracts-v1.md。
 */
@Tag(name = "M04 同步中枢")
@RestController
@RequestMapping("/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @Operation(summary = "入站: 接收对端(EBMS)变更")
    @PostMapping("/inbound")
    public Result<Map<String, Object>> inbound(@RequestBody InboundRequest req) {
        return Result.success(syncService.inbound(req));
    }

    @Operation(summary = "冲突列表")
    @GetMapping("/conflicts")
    public Result<List<SyncConflict>> conflicts(@RequestParam(required = false) String resolution) {
        return Result.success(syncService.listConflicts(resolution));
    }

    @Operation(summary = "人工裁决冲突")
    @PostMapping("/conflicts/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody ResolveReq body) {
        syncService.resolveConflict(id, body.getResolution(), body.getNote(), body.getResolvedBy());
        return Result.success();
    }

    @Operation(summary = "出站: 待推送事件")
    @GetMapping("/outbound/pending")
    public Result<List<Map<String, Object>>> pending(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(syncService.outboundPending(limit));
    }

    @Operation(summary = "出站: 对端确认消费")
    @PostMapping("/outbound/ack")
    public Result<Void> ack(@RequestBody Map<String, List<String>> body) {
        syncService.ackOutbound(body.get("eventIds"));
        return Result.success();
    }

    @Operation(summary = "触发对账")
    @PostMapping("/reconcile")
    public Result<Map<String, Object>> reconcile(@RequestParam(required = false) String objectType) {
        return Result.success(syncService.reconcile(objectType));
    }

    @Operation(summary = "同步概览")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(syncService.stats());
    }

    @Data
    public static class ResolveReq {
        private String resolution;
        private String note;
        private String resolvedBy;
    }
}
