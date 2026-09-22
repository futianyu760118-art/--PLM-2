package com.hjgd.plm.metric.service;

import java.util.List;
import java.util.Map;

public interface KpiSettlementService {

    /**
     * KPI 周期结算：对全部启用 KPI，按其 period_type 聚合所属 metric_value，
     * 取周期内最新日值作为 actual_value，按阈值与方向判定 GREEN/YELLOW/RED，
     * 幂等 upsert 进 plm_kpi_value。
     *
     * @return 各 kpi_code → {actual, status}
     */
    Map<String, Map<String, Object>> settle();
}
