package com.hjgd.plm.governance.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.governance.entity.DataProduct;
import com.hjgd.plm.governance.entity.MetricObject;
import com.hjgd.plm.governance.service.RegistryService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 指标对象（F13）与数据产品（F14）注册与治理核对接口。 */
@Tag(name = "AEOS-指标对象与数据产品治理")
@RestController
@RequestMapping("/registry")
@RequiredArgsConstructor
public class RegistryController {

    private final RegistryService registryService;

    @Operation(summary = "指标对象清单")
    @GetMapping("/metrics")
    public Result<List<MetricObject>> metrics() {
        return Result.success(registryService.listMetricObjects());
    }

    @Operation(summary = "指标对象详情")
    @GetMapping("/metrics/{metricCode}")
    public Result<MetricObject> metric(@PathVariable String metricCode) {
        return Result.success(registryService.getMetricObject(metricCode));
    }

    @Operation(summary = "新增/更新指标对象（强制治理要素）")
    @OperationLog(value = "保存指标对象")
    @PostMapping("/metrics")
    public Result<MetricObject> saveMetric(@RequestBody MetricObject req) {
        return Result.success(registryService.saveMetricObject(req));
    }

    @Operation(summary = "指标对象完整性核对（缺项计数）")
    @GetMapping("/metrics/check")
    public Result<Map<String, Object>> checkMetrics() {
        return Result.success(registryService.checkMetricObjects());
    }

    @Operation(summary = "数据产品清单")
    @GetMapping("/data-products")
    public Result<List<DataProduct>> dataProducts() {
        return Result.success(registryService.listDataProducts());
    }

    @Operation(summary = "数据产品详情")
    @GetMapping("/data-products/{productCode}")
    public Result<DataProduct> dataProduct(@PathVariable String productCode) {
        return Result.success(registryService.getDataProduct(productCode));
    }

    @Operation(summary = "新增/更新数据产品（强制唯一真相源与服务契约）")
    @OperationLog(value = "保存数据产品")
    @PostMapping("/data-products")
    public Result<DataProduct> saveDataProduct(@RequestBody DataProduct req) {
        return Result.success(registryService.saveDataProduct(req));
    }

    @Operation(summary = "数据产品完整性核对（缺项计数）")
    @GetMapping("/data-products/check")
    public Result<Map<String, Object>> checkDataProducts() {
        return Result.success(registryService.checkDataProducts());
    }
}
