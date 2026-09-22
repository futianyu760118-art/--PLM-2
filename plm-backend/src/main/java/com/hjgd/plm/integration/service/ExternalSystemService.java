package com.hjgd.plm.integration.service;

import java.util.List;
import java.util.Map;

/**
 * 外部系统对接统一接口 (ERP / MES / CRM / SPC)
 * 各实现类按目标系统适配, 主后台调用统一接口规范
 */
public interface ExternalSystemService {

    String getSystemCode();

    boolean testConnection();

    Map<String, Object> syncMaterial(String partNo);

    List<Map<String, Object>> queryInventory(String partNo);

    Map<String, Object> pushProductionOrder(Map<String, Object> order);

    Map<String, Object> getSystemInfo();
}
