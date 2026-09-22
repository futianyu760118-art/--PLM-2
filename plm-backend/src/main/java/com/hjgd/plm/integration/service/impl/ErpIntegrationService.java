package com.hjgd.plm.integration.service.impl;

import com.hjgd.plm.integration.service.ExternalSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * ERP 系统对接实现 (预留标准接口)
 * 默认提供模拟数据,实际对接时配置 erp.base-url 后启用
 */
@Slf4j
@Service
public class ErpIntegrationService implements ExternalSystemService {

    @Value("${plm.integration.erp.base-url:}")
    private String baseUrl;

    @Value("${plm.integration.erp.enabled:false}")
    private boolean enabled;

    @Override
    public String getSystemCode() {
        return "ERP";
    }

    @Override
    public boolean testConnection() {
        if (!enabled || baseUrl.isEmpty()) {
            log.info("ERP对接未启用, 返回模拟连通");
            return true;
        }
        try {
            RestClient.create(baseUrl).get().uri("/health").retrieve().toEntity(String.class);
            return true;
        } catch (Exception e) {
            log.error("ERP连接测试失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> syncMaterial(String partNo) {
        log.info("同步物料[{}]至ERP", partNo);
        if (enabled && !baseUrl.isEmpty()) {
            try {
                return RestClient.create(baseUrl).post()
                        .uri("/api/material/sync")
                        .body(Map.of("partNo", partNo))
                        .retrieve()
                        .body(Map.class);
            } catch (Exception e) {
                log.warn("ERP同步失败,返回模拟数据: {}", e.getMessage());
            }
        }
        return Map.of(
                "partNo", partNo,
                "erpStatus", "SYNCED",
                "message", "物料已同步至ERP(模拟)"
        );
    }

    @Override
    public List<Map<String, Object>> queryInventory(String partNo) {
        if (enabled && !baseUrl.isEmpty()) {
            try {
                ResponseEntity<Map> resp = RestClient.create(baseUrl).get()
                        .uri("/api/inventory/" + partNo)
                        .retrieve()
                        .toEntity(Map.class);
                Object data = resp.getBody() != null ? resp.getBody().get("data") : null;
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            } catch (Exception e) {
                log.warn("ERP库存查询失败: {}", e.getMessage());
            }
        }
        return List.of(
                Map.of("warehouse", "原料仓", "quantity", 12000, "unit", "PCS"),
                Map.of("warehouse", "成品仓", "quantity", 3500, "unit", "PCS")
        );
    }

    @Override
    public Map<String, Object> pushProductionOrder(Map<String, Object> order) {
        log.info("推送生产订单至ERP: {}", order.get("orderNo"));
        return Map.of("orderNo", order.get("orderNo"), "erpStatus", "PUSHED", "message", "订单已推送(模拟)");
    }

    @Override
    public Map<String, Object> getSystemInfo() {
        return Map.of(
                "systemCode", "ERP",
                "enabled", enabled,
                "baseUrl", baseUrl.isEmpty() ? "(未配置)" : baseUrl,
                "description", "ERP系统对接 - 物料同步/库存查询/生产订单"
        );
    }
}
