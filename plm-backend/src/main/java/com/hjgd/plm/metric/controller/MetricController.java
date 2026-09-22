package com.hjgd.plm.metric.controller;

import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "V1 度量KPI")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MetricController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "指标目录")
    @GetMapping("/metrics/defs")
    public Result<List<Map<String, Object>>> metricDefs() {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT metric_code, name, category, unit, direction, grain, enabled FROM plm_metric_def WHERE enabled=true ORDER BY id"));
        } catch (Exception e) {
            return Result.success(builtinMetrics());
        }
    }

    @Operation(summary = "通用指标查询(BI唯一出口骨架)")
    @PostMapping("/metrics/query")
    public Result<Map<String, Object>> query(@RequestBody MetricQueryReq req) {
        List<Map<String, Object>> series = new ArrayList<>();
        if (req.getMetricCodes() != null) {
            for (String code : req.getMetricCodes()) {
                List<Map<String, Object>> points = new ArrayList<>();
                try {
                    points = jdbcTemplate.queryForList(
                            "SELECT grain_time AS t, value_calc AS value, dim_json AS dims FROM plm_metric_value WHERE metric_code=? ORDER BY grain_time DESC LIMIT 90",
                            code);
                } catch (Exception ignored) {
                }
                // 实时兜底：质量均分
                if (points.isEmpty() && "M_DQ_SCORE".equals(code)) {
                    try {
                        Double avg = jdbcTemplate.queryForObject(
                                "SELECT AVG(score_0_100) FROM plm_dq_object_score", Double.class);
                        points.add(Map.of("t", java.time.LocalDate.now().toString(),
                                "value", avg == null ? 0 : avg, "dims", Map.of()));
                    } catch (Exception ignored) {
                    }
                }
                series.add(Map.of("metricCode", code, "points", points));
            }
        }
        return Result.success(Map.of("series", series));
    }

    @Operation(summary = "KPI 目录")
    @GetMapping("/kpis/defs")
    public Result<List<Map<String, Object>>> kpiDefs() {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT kpi_code, name, metric_code, period_type, threshold_green, threshold_yellow, threshold_red FROM plm_kpi_def WHERE enabled=true"));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "KPI 当前值")
    @GetMapping("/kpis/values")
    public Result<List<Map<String, Object>>> kpiValues(@RequestParam(required = false) String periodKey,
                                                       @RequestParam(required = false) String kpiCodes) {
        try {
            // 指定 periodKey：按精确周期返回(周/月/季各自键值)
            if (periodKey != null && !periodKey.isBlank()) {
                return Result.success(jdbcTemplate.queryForList(
                        "SELECT * FROM plm_kpi_value WHERE period_key=? ORDER BY kpi_code", periodKey));
            }
            // 未指定：返回每个 KPI 的最新结算值(周类与月类 KPI 各取自己的当前周期，驾驶舱用)
            List<Map<String, Object>> all = jdbcTemplate.queryForList(
                    "SELECT DISTINCT ON (kpi_code) * FROM plm_kpi_value ORDER BY kpi_code, period_key DESC");
            return Result.success(filterByCodes(all, kpiCodes));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    private List<Map<String, Object>> filterByCodes(List<Map<String, Object>> rows, String kpiCodes) {
        if (kpiCodes == null || kpiCodes.isBlank()) {
            return rows;
        }
        Set<String> want = java.util.Arrays.stream(kpiCodes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(r -> want.contains(String.valueOf(r.get("kpi_code")))).toList();
    }

    private List<Map<String, Object>> builtinMetrics() {
        return List.of(
                Map.of("metric_code", "M_DQ_SCORE", "name", "主数据质量均分", "category", "QUALITY"),
                Map.of("metric_code", "M_ECN_CYCLE_H", "name", "ECN平均闭环小时", "category", "CYCLE"),
                Map.of("metric_code", "M_CODE_AUTO_RATE", "name", "自动编号率", "category", "LEAN")
        );
    }

    @Data
    public static class MetricQueryReq {
        private List<String> metricCodes;
        private Map<String, Object> timeRange;
        private List<String> dimensions;
        private Map<String, Object> filters;
    }
}
