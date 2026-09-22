package com.hjgd.plm.metric.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.metric.service.KpiSettlementService;
import com.hjgd.plm.metric.service.MetricRollupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * V1.1 定时任务手动触发 + 度量排错入口。
 * 验收用：白天可手动跑一次度量日批 / DQ 夜检，无需等到凌晨。
 */
@Tag(name = "V1 度量Job")
@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final MetricRollupService metricRollupService;
    private final KpiSettlementService kpiSettlementService;
    private final DataQualityService dataQualityService;

    @Operation(summary = "手动触发 DQ 全量夜检")
    @PostMapping("/dq/scan")
    public Result<Map<String, Object>> dqScan() {
        int n = dataQualityService.runFullScan("MANUAL");
        return Result.success(Map.of("scanned", n));
    }

    @Operation(summary = "手动触发度量日批")
    @PostMapping("/metric/rollup")
    public Result<Map<String, Object>> metricRollup() {
        return Result.success(metricRollupService.runDaily());
    }

    @Operation(summary = "手动触发 KPI 周期结算")
    @PostMapping("/kpi/settle")
    public Result<Map<String, Map<String, Object>>> kpiSettle() {
        return Result.success(kpiSettlementService.settle());
    }

    @Operation(summary = "指标试算(不落库)，用于排错")
    @GetMapping("/metric/dry-run")
    public Result<List<Map<String, Object>>> dryRun(@RequestParam String metricCode) {
        return Result.success(metricRollupService.dryRun(metricCode));
    }
}
