package com.hjgd.plm.integration.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.integration.service.ExternalSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "外部系统对接")
@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final List<ExternalSystemService> services;

    @Operation(summary = "所有对接系统状态")
    @GetMapping("/status")
    public Result<List<Map<String, Object>>> status() {
        List<Map<String, Object>> list = services.stream().map(s -> {
            Map<String, Object> m = new HashMap<>(s.getSystemInfo());
            m.put("connected", s.testConnection());
            return m;
        }).toList();
        return Result.success(list);
    }

    @Operation(summary = "测试系统连接")
    @GetMapping("/{system}/test")
    public Result<Boolean> test(@PathVariable String system) {
        return Result.success(find(system).testConnection());
    }

    @Operation(summary = "同步物料至指定系统")
    @PostMapping("/{system}/material/{partNo}/sync")
    public Result<Map<String, Object>> syncMaterial(@PathVariable String system, @PathVariable String partNo) {
        return Result.success(find(system).syncMaterial(partNo));
    }

    @Operation(summary = "查询库存(从ERP)")
    @GetMapping("/erp/inventory/{partNo}")
    public Result<List<Map<String, Object>>> inventory(@PathVariable String partNo) {
        return Result.success(find("ERP").queryInventory(partNo));
    }

    @Operation(summary = "推送生产订单")
    @PostMapping("/{system}/production-order")
    public Result<Map<String, Object>> pushOrder(@PathVariable String system, @RequestBody Map<String, Object> order) {
        return Result.success(find(system).pushProductionOrder(order));
    }

    private ExternalSystemService find(String code) {
        return services.stream()
                .filter(s -> s.getSystemCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new com.hjgd.plm.common.BusinessException("未找到系统: " + code));
    }
}
