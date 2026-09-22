package com.hjgd.plm.exportx.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("导出 SQL 构建(物料清单按 spec 过滤)")
class ExportServiceTest {

    @Test
    @DisplayName("无 spec: 导出全部物料")
    void noSpecExportsAll() {
        String sql = ExportService.buildPartQuery(Map.of());
        assertTrue(sql.contains("FROM plm_material WHERE deleted=0"));
        assertFalse(sql.contains("AND status"));
        assertTrue(sql.endsWith("LIMIT 10000"));
    }

    @Test
    @DisplayName("验收: 已发布成品清单 = status=RELEASED + materialType=FINISHED")
    void releasedFinishedProductsFilter() {
        String sql = ExportService.buildPartQuery(Map.of("status", "RELEASED", "materialType", "FINISHED"));
        assertTrue(sql.contains("AND status='RELEASED'"));
        assertTrue(sql.contains("AND material_type='FINISHED'"));
    }

    @Test
    @DisplayName("灯具品类过滤: productType=FL")
    void productTypeFilter() {
        String sql = ExportService.buildPartQuery(Map.of("productType", "FL"));
        assertTrue(sql.contains("AND product_type='FL'"));
    }

    @Test
    @DisplayName("防注入: 非法字符被剥离")
    void injectionSanitized() {
        String sql = ExportService.buildPartQuery(Map.of("status", "RELEASED'; DROP TABLE x;--"));
        // 仅保留 RELEASED，DROP 被剥离
        assertTrue(sql.contains("AND status='RELEASED'"));
        assertFalse(sql.contains("DROP"));
        assertFalse(sql.contains(";"));
    }

    @Test
    @DisplayName("resource 路由: part/ecn/未知")
    void resourceRouting() {
        assertTrue(ExportService.buildQuerySql("part", Map.of()).contains("plm_material"));
        assertTrue(ExportService.buildQuerySql("ecn", Map.of()).contains("plm_ecn"));
        assertEquals("SELECT 1 LIMIT 0", ExportService.buildQuerySql("unknown", Map.of()));
    }
}
