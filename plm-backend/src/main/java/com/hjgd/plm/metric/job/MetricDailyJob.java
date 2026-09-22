package com.hjgd.plm.metric.job;

import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.metric.service.MetricRollupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 度量日批：默认关闭，生产配置 plm.job.metric.cron 后生效（如 "0 30 1 * * ?"）。
 * ② 必须排在 DQ 夜检之后，以读当晚最新质量分。cron "-" 表示禁用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricDailyJob {

    private final MetricRollupService metricRollupService;
    private final JobProperties jobProperties;

    @Scheduled(cron = "${plm.job.metric.cron:-}")
    public void run() {
        if (!jobProperties.getMetric().isEnabled()) {
            return;
        }
        log.info("[JOB] metric-daily-rollup start");
        try {
            metricRollupService.runDaily();
        } catch (Exception e) {
            log.error("[JOB] metric-daily-rollup error", e);
        }
    }
}
