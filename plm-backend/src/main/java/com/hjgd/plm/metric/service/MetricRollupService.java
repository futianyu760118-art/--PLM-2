package com.hjgd.plm.metric.service;

import java.util.List;
import java.util.Map;

public interface MetricRollupService {

    /**
     * 度量日批：扫描全库计算全部可算指标，按 grain_time=今天 写入 plm_metric_value。
     * 同日同 metric_code 幂等覆盖（先删当日再写）。
     *
     * @return 各 metric_code → 写入的 value_calc
     */
    Map<String, Object> runDaily();

    /**
     * 仅计算单个指标（供手动触发/排错），不落库。
     */
    List<Map<String, Object>> dryRun(String metricCode);
}
