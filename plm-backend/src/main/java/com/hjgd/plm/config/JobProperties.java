package com.hjgd.plm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * V1.1 度量日批 / DQ 夜检 / KPI 结算 定时任务配置。
 * 全部带开关，便于灰度与测试环境关闭。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plm.job")
public class JobProperties {

    /** ① 度量日批：每晚 01:30 扫全库写 plm_metric_value（须排在 DQ 夜检之后） */
    private Rollup metric = new Rollup();

    /** ① DQ 全量夜检：每晚 01:00 刷 plm_dq_object_score + 开 INFO/WARN 债务（最先执行） */
    private Rollup dq = new Rollup();

    /** ③ KPI 周期结算：每晚 02:00 聚合 metric_value 写 plm_kpi_value 红黄绿（最后执行） */
    private Rollup kpi = new Rollup();

    /** 草稿超龄判定天数（创建超过该天数仍处 DRAFT/REVIEWING 视为超龄 WIP） */
    private int draftAgedDays = 7;

    /** ECN 周期滚动窗口天数（计算近 N 天生效 ECN 的平均闭环小时） */
    private int ecnCycleWindowDays = 30;

    @Data
    public static class Rollup {
        /** V1.1 默认开启，使度量/KPI 在部署后即自动运行 */
        private boolean enabled = true;
        /** Spring cron 表达式（6 字段：秒 分 时 日 月 周），空则不调度 */
        private String cron = "";
    }
}
