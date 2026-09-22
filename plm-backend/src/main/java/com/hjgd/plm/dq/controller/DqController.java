package com.hjgd.plm.dq.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.DqAutoFixService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "V1 数据自检")
@RestController
@RequestMapping("/v1/dq")
@RequiredArgsConstructor
public class DqController {

    private final MaterialService materialService;
    private final DqAutoFixService dqAutoFixService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "对零件执行自检(objectId 支持id或partNo)")
    @PostMapping("/run")
    public Result<DqRunResult> run(@RequestBody RunReq req) {
        if (!"PART".equalsIgnoreCase(req.getObjectType())) {
            return Result.failed("当前仅支持 PART");
        }
        Material m = resolvePart(req.getObjectId());
        if (m == null) {
            return Result.failed(404, "零件不存在: " + req.getObjectId());
        }
        return Result.success(materialService.checkQuality(m.getId()));
    }

    private Material resolvePart(String idOrPartNo) {
        if (idOrPartNo == null || idOrPartNo.isBlank()) return null;
        try {
            return materialService.getById(Long.parseLong(idOrPartNo));
        } catch (NumberFormatException e) {
            return materialService.getByPartNo(idOrPartNo);
        } catch (Exception e) {
            return materialService.getByPartNo(idOrPartNo);
        }
    }

    @Operation(summary = "质量分")
    @GetMapping("/scores")
    public Result<List<Map<String, Object>>> scores(@RequestParam String objectType,
                                                    @RequestParam(required = false) String objectId) {
        try {
            if (objectId != null) {
                return Result.success(jdbcTemplate.queryForList(
                        "SELECT * FROM plm_dq_object_score WHERE object_type=? AND object_id=?",
                        objectType, objectId));
            }
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT * FROM plm_dq_object_score WHERE object_type=? ORDER BY updated_at DESC LIMIT 200",
                    objectType));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "最近自检运行")
    @GetMapping("/runs")
    public Result<List<Map<String, Object>>> runs(@RequestParam String objectType,
                                                  @RequestParam String objectId) {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT * FROM plm_dq_run WHERE object_type=? AND object_id=? ORDER BY id DESC LIMIT 100",
                    objectType, objectId));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "质量债务列表")
    @GetMapping("/debts")
    public Result<List<Map<String, Object>>> debts(@RequestParam(required = false) String status) {
        try {
            if (status != null && !status.isBlank()) {
                return Result.success(jdbcTemplate.queryForList(
                        "SELECT * FROM plm_dq_debt WHERE status=? ORDER BY created_at DESC LIMIT 200", status));
            }
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT * FROM plm_dq_debt ORDER BY created_at DESC LIMIT 200"));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "DQ规则列表")
    @GetMapping("/rules")
    public Result<List<Map<String, Object>>> rules() {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT rule_code, name, object_type, severity, check_type, expression, message_template, enabled, auto_fix_strategy FROM plm_dq_rule ORDER BY id"));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "对单条债务尝试自动修复(原始→修复→回检→确认闭环)")
    @PostMapping("/fix/{debtId}")
    public Result<DqAutoFixService.FixAttemptResult> fix(@PathVariable Long debtId) {
        return Result.success(dqAutoFixService.tryFix(debtId, "USER"));
    }

    @Operation(summary = "批量修复 TOP N 债务(默认5)")
    @PostMapping("/fix/batch")
    public Result<Map<String, Object>> fixBatch(@RequestParam(defaultValue = "5") int limit) {
        int ok = dqAutoFixService.fixTopDebts(limit, "USER");
        return Result.success(Map.of("attempted", limit, "resolved", ok));
    }

    @Operation(summary = "修复尝试记录(含原始问题关联)")
    @GetMapping("/fix/attempts")
    public Result<List<Map<String, Object>>> fixAttempts(@RequestParam(required = false) Long debtId,
                                                         @RequestParam(required = false) Boolean resolved) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, source_debt_id, rule_code, object_type, object_id, strategy, " +
                        "before_state, after_state, before_score, after_score, resolved, " +
                        "linked_issue_id, error_msg, attempted_by, created_at, confirmed_at " +
                        "FROM plm_dq_fix_attempt WHERE 1=1 ");
        List<Object> args = new java.util.ArrayList<>();
        if (debtId != null) {
            sql.append("AND source_debt_id=? ");
            args.add(debtId);
        }
        if (resolved != null) {
            sql.append("AND resolved=? ");
            args.add(resolved);
        }
        sql.append("ORDER BY id DESC LIMIT 200");
        return Result.success(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @Data
    public static class RunReq {
        private String objectType;
        private String objectId;
        private String trigger;
    }
}
