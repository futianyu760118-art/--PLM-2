package com.hjgd.plm.metric.job;

import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.metric.service.KpiSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KPI 周期结算：③ 最后执行（02:00），聚合当周/当月 metric_value 成红黄绿。
 * 必须排在度量日批之后。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiPeriodCloseJob {

    private final KpiSettlementService kpiSettlementService;
    private final JobProperties jobProperties;

    @Scheduled(cron = "${plm.job.kpi.cron:-}")
    public void run() {
        if (!jobProperties.getKpi().isEnabled()) {
            return;
        }
        log.info("[JOB] kpi-period-close start");
        try {
            kpiSettlementService.settle();
        } catch (Exception e) {
            log.error("[JOB] kpi-period-close error", e);
        }
    }
}
