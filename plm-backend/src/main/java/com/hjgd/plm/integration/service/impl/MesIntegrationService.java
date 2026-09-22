package com.hjgd.plm.integration.service.impl;

import com.hjgd.plm.integration.service.ExternalSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MES 制造执行系统对接 (预留)
 */
@Slf4j
@Service
public class MesIntegrationService implements ExternalSystemService {

    @Value("${plm.integration.mes.base-url:}")
    private String baseUrl;
    @Value("${plm.integration.mes.enabled:false}")
    private boolean enabled;

    @Override
    public String getSystemCode() { return "MES"; }

    @Override
    public boolean testConnection() {
        return true;
    }

    @Override
    public Map<String, Object> syncMaterial(String partNo) {
        return Map.of("partNo", partNo, "mesStatus", "SYNCED", "message", "工艺SOP已下发MES(模拟)");
    }

    @Override
    public List<Map<String, Object>> queryInventory(String partNo) {
        return List.of(Map.of("line", "注塑A线", "status", "运行中", "output", 1500));
    }

    @Override
    public Map<String, Object> pushProductionOrder(Map<String, Object> order) {
        return Map.of("orderNo", order.get("orderNo"), "mesStatus", "PUSHED");
    }

    @Override
    public Map<String, Object> getSystemInfo() {
        return Map.of(
                "systemCode", "MES",
                "enabled", enabled,
                "baseUrl", baseUrl.isEmpty() ? "(未配置)" : baseUrl,
                "description", "MES制造执行 - 工艺SOP下发/产线数据/不良率"
        );
    }
}
