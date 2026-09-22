package com.hjgd.plm.analytics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.analytics.entity.AnalyticsChart;
import com.hjgd.plm.analytics.entity.AnalyticsDataset;
import com.hjgd.plm.analytics.mapper.AnalyticsChartMapper;
import com.hjgd.plm.analytics.mapper.AnalyticsDatasetMapper;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@Tag(name = "V1 分析图表(配置化)")
@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsDatasetMapper datasetMapper;
    private final AnalyticsChartMapper chartMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Operation(summary = "数据集列表")
    @GetMapping("/datasets")
    public Result<List<AnalyticsDataset>> listDatasets() {
        return Result.success(datasetMapper.selectList(
                new LambdaQueryWrapper<AnalyticsDataset>().eq(AnalyticsDataset::getEnabled, true)
                        .orderByAsc(AnalyticsDataset::getId)));
    }

    @Operation(summary = "创建数据集")
    @PostMapping("/datasets")
    public Result<AnalyticsDataset> createDataset(@RequestBody AnalyticsDataset ds) {
        if (ds.getEnabled() == null) ds.setEnabled(true);
        datasetMapper.insert(ds);
        return Result.success(ds);
    }

    @Operation(summary = "数据集执行(SQL)")
    @PostMapping("/datasets/{code}/data")
    public Result<List<Map<String, Object>>> datasetData(@PathVariable String code,
                                                         @RequestBody(required = false) Map<String, Object> params) {
        AnalyticsDataset ds = datasetMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsDataset>().eq(AnalyticsDataset::getDatasetCode, code));
        if (ds == null) return Result.failed(404, "数据集不存在: " + code);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(ds.getSqlText());
            return Result.success(rows);
        } catch (Exception e) {
            return Result.failed("数据集执行失败: " + e.getMessage());
        }
    }

    @Operation(summary = "图表列表")
    @GetMapping("/charts")
    public Result<List<AnalyticsChart>> listCharts() {
        return Result.success(chartMapper.selectList(
                new LambdaQueryWrapper<AnalyticsChart>().eq(AnalyticsChart::getEnabled, true)
                        .orderByAsc(AnalyticsChart::getId)));
    }

    @Operation(summary = "创建图表")
    @PostMapping("/charts")
    public Result<AnalyticsChart> createChart(@RequestBody AnalyticsChart chart) {
        if (chart.getEnabled() == null) chart.setEnabled(true);
        chartMapper.insert(chart);
        return Result.success(chart);
    }

    @Operation(summary = "图表元数据")
    @GetMapping("/charts/{code}")
    public Result<Map<String, Object>> chartMeta(@PathVariable String code) {
        AnalyticsChart chart = chartMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsChart>().eq(AnalyticsChart::getChartCode, code));
        if (chart == null) return Result.failed(404, "图表不存在: " + code);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("chartCode", chart.getChartCode());
        meta.put("title", chart.getTitle());
        meta.put("datasetCode", chart.getDatasetCode());
        meta.put("chartType", chart.getChartType());
        try {
            if (chart.getEncodeJson() != null && !chart.getEncodeJson().isBlank()) {
                meta.put("encode", objectMapper.readValue(chart.getEncodeJson(), Map.class));
            }
        } catch (Exception e) {
            meta.put("encode", Map.of("error", "encode parse failed: " + e.getMessage()));
        }
        return Result.success(meta);
    }

    @Operation(summary = "图表数据(取数据集 + 模板)")
    @PostMapping("/charts/{code}/data")
    public Result<Map<String, Object>> chartData(@PathVariable String code,
                                                  @RequestBody(required = false) Map<String, Object> params) {
        AnalyticsChart chart = chartMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsChart>().eq(AnalyticsChart::getChartCode, code));
        if (chart == null) return Result.failed(404, "图表不存在: " + code);
        AnalyticsDataset ds = datasetMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsDataset>().eq(AnalyticsDataset::getDatasetCode, chart.getDatasetCode()));
        if (ds == null) return Result.failed(404, "图表关联数据集不存在: " + chart.getDatasetCode());

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(ds.getSqlText());
        } catch (Exception e) {
            rows = List.of(Map.of("error", e.getMessage()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", chart.getTitle());
        result.put("chartType", chart.getChartType());
        result.put("rows", rows);
        return Result.success(result);
    }
}
