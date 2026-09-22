package com.hjgd.plm.metric.job;

import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.dq.service.DqAutoFixService;
import com.hjgd.plm.improve.service.InsightGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DQ 全量夜检：① 最先执行（01:00），刷 plm_dq_object_score + 开 INFO/WARN 债务，
 * 随即聚合 TOP 债务生成洞察(HIGH 自动转 ISSUE)。
 * 最后尝试自动修复 TOP 5 债务(单条容错)并回检关闭已解决项。
 * 必须排在度量日批②之前——后者读当晚最新质量分。
 * 默认关闭，生产配置 plm.job.dq.enabled=true 后生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DqNightScanJob {

    private final DataQualityService dataQualityService;
    private final InsightGenerator insightGenerator;
    private final DqAutoFixService dqAutoFixService;
    private final JobProperties jobProperties;

    @Scheduled(cron = "${plm.job.dq.cron:-}")
    public void run() {
        if (!jobProperties.getDq().isEnabled()) {
            return;
        }
        log.info("[JOB] dq-night-scan start");
        try {
            int scanned = dataQualityService.runFullScan("NIGHTLY");
            int insights = insightGenerator.generateFromDqDebt();
            int fixed = dqAutoFixService.fixTopDebts(5, "AUTO");
            log.info("[JOB] dq-night-scan done scanned={} insights={} fixed={}", scanned, insights, fixed);
        } catch (Exception e) {
            log.error("[JOB] dq-night-scan error", e);
        }
    }
}
