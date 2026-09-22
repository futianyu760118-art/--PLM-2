package com.hjgd.plm.integration.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "V1 集成接口(EBMS/ERP)")
@RestController
@RequestMapping("/v1/integration")
@RequiredArgsConstructor
public class IntegrationV1Controller {

    private final MaterialService materialService;
    private final JdbcTemplate jdbcTemplate;
    private final com.hjgd.plm.bom.service.BomService bomService;

    @Operation(summary = "零件状态摘要")
    @GetMapping("/parts/{partNo}")
    public Result<Map<String, Object>> getPart(@PathVariable String partNo) {
        Material m = materialService.getByPartNo(partNo);
        if (m == null) {
            return Result.failed(404, "料号不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partNo", m.getPartNo());
        data.put("nameZh", m.getMaterialName());
        data.put("nameEn", m.getNameEn());
        data.put("lifecycleState", m.getStatus() == null ? null : m.getStatus().name());
        data.put("versionNo", m.getVersionNo());
        data.put("phase", m.getPhase());
        data.put("partCategory", m.getPartCategory());
        data.put("productType", m.getProductType());
        data.put("materialType", m.getMaterialType() == null ? null : m.getMaterialType().name());
        data.put("unit", m.getUnit());
        data.put("projectNo", m.getProjectNo());
        return Result.success(data);
    }

    @Operation(summary = "批量 lookup")
    @PostMapping("/parts/lookup")
    public Result<List<Map<String, Object>>> lookup(@RequestBody LookupBody body) {
        if (body.getPartNos() == null) {
            return Result.success(List.of());
        }
        List<Map<String, Object>> list = body.getPartNos().stream()
                .map(materialService::getByPartNo)
                .filter(Objects::nonNull)
                .map(m -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("partNo", m.getPartNo());
                    d.put("nameZh", m.getMaterialName());
                    d.put("lifecycleState", m.getStatus() == null ? null : m.getStatus().name());
                    d.put("versionNo", m.getVersionNo());
                    d.put("phase", m.getPhase());
                    return d;
                })
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @Operation(summary = "成本BOM视图(CBOM真实展开,EBMS算毛利用)")
    @GetMapping("/parts/{partNo}/cbom")
    public Result<Map<String, Object>> cbom(@PathVariable String partNo) {
        Material m = materialService.getByPartNo(partNo);
        if (m == null) {
            return Result.failed(404, "料号不存在");
        }
        List<Map<String, Object>> lines = bomService.cbomExpand(partNo);
        java.math.BigDecimal totalCost = java.math.BigDecimal.ZERO;
        for (Map<String, Object> l : lines) {
            Object ext = l.get("extendedCost");
            if (ext instanceof java.math.BigDecimal b) {
                totalCost = totalCost.add(b);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partNo", partNo);
        data.put("versionNo", m.getVersionNo());
        data.put("lifecycleState", m.getStatus() == null ? null : m.getStatus().name());
        data.put("bomType", "EBOM");
        data.put("lineCount", lines.size());
        data.put("totalCost", totalCost);
        data.put("currency", m.getCostCurrency() == null ? "CNY" : m.getCostCurrency());
        data.put("lines", lines);
        return Result.success(data);
    }

    @Operation(summary = "订单锁版本")
    @PostMapping("/orders/lock-version")
    public Result<Map<String, Object>> lockVersion(@RequestBody LockBody body) {
        Material m = materialService.getByPartNo(body.getPartNo());
        if (m == null) {
            return Result.failed(404, "料号不存在");
        }
        String state = m.getStatus() == null ? "" : m.getStatus().name();
        if (!Set.of("RELEASED", "IN_PRODUCTION").contains(state)) {
            return Result.failed(409, "料号未发布/量产，禁止锁版本: " + state);
        }
        String ver = StringUtilsOr(body.getVersionNo(), m.getVersionNo());
        try {
            jdbcTemplate.update(
                    """
                    CREATE TABLE IF NOT EXISTS plm_order_version_lock (
                      id BIGSERIAL PRIMARY KEY,
                      order_no VARCHAR(64) NOT NULL,
                      part_no VARCHAR(64) NOT NULL,
                      version_no VARCHAR(32) NOT NULL,
                      locked_at TIMESTAMPTZ DEFAULT NOW(),
                      UNIQUE(order_no, part_no)
                    )
                    """);
            jdbcTemplate.update(
                    """
                    INSERT INTO plm_order_version_lock(order_no, part_no, version_no)
                    VALUES(?,?,?)
                    ON CONFLICT(order_no, part_no) DO UPDATE SET version_no=EXCLUDED.version_no, locked_at=NOW()
                    """,
                    body.getOrderNo(), body.getPartNo(), ver);
        } catch (Exception e) {
            return Result.failed("锁版本写入失败: " + e.getMessage());
        }
        return Result.success(Map.of(
                "orderNo", body.getOrderNo(),
                "partNo", body.getPartNo(),
                "versionNo", ver,
                "lifecycleState", state
        ));
    }

    @Operation(summary = "查询订单锁")
    @GetMapping("/orders/{orderNo}/lock")
    public Result<List<Map<String, Object>>> getLock(@PathVariable String orderNo) {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT * FROM plm_order_version_lock WHERE order_no=?", orderNo));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "事件目录")
    @GetMapping("/event-catalog")
    public Result<List<Map<String, String>>> eventCatalog() {
        return Result.success(List.of(
                Map.of("eventType", "part.created", "desc", "零件创建"),
                Map.of("eventType", "part.state_changed", "desc", "生命周期变更"),
                Map.of("eventType", "part.released", "desc", "正式发布"),
                Map.of("eventType", "ecn.effective", "desc", "ECN生效")
        ));
    }

    @Operation(summary = "拉取待消费领域事件")
    @GetMapping("/events/pending")
    public Result<List<Map<String, Object>>> pendingEvents(@RequestParam(defaultValue = "50") int limit) {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT id, event_id, event_type, aggregate_type, aggregate_id, payload_json, created_at FROM plm_domain_event WHERE status='NEW' ORDER BY id ASC LIMIT ?",
                    Math.min(limit, 200)));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    private static String StringUtilsOr(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    @Data
    public static class LookupBody {
        private List<String> partNos;
    }

    @Data
    public static class LockBody {
        private String orderNo;
        private String partNo;
        private String versionNo;
    }
}
