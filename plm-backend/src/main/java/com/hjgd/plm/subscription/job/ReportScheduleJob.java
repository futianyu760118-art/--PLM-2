package com.hjgd.plm.subscription.job;

import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.subscription.mapper.PlmSubscriptionMapper;
import com.hjgd.plm.subscription.service.ReportService;
import com.hjgd.plm.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期报告定时生成（AC-B4-3、AC-B5-1 偏差 ≤10 分钟）。
 *
 * <p>每分钟扫一次「到期的订阅」而不是给每条订阅注册一个定时任务：
 * 订阅的粒度最细到自然日，扫表的代价可以忽略；而改周期/暂停/恢复只需重算一个游标时间戳，
 * 不必迁移已有任务（AC-B4-6/B4-7）。
 *
 * <p>游标无条件前移：某一期即使生成失败也不再重试该期（失败已经通知订阅人，AC-B5-5），
 * 否则一条坏数据会把这条订阅永远卡死在同一期，之后所有周期都发不出去。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportScheduleJob {

    /** 单轮最多处理的到期订阅数，避免一次扫描把所有订阅的报告生成堆在一个周期里 */
    private static final int BATCH = 200;

    private final SubscriptionService subscriptionService;
    private final ReportService reportService;
    private final PlmSubscriptionMapper subscriptionMapper;
    private final SubscriptionProperties properties;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        if (!properties.isScheduleEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PlmSubscription> due;
        try {
            due = subscriptionService.listDue(now, BATCH);
        } catch (Exception e) {
            log.error("扫描到期订阅失败: {}", e.toString(), e);
            return;
        }
        for (PlmSubscription subscription : due) {
            try {
                catchUp(subscription, now);
            } catch (Exception e) {
                // 一条订阅出错不能中断整轮扫描
                log.error("周期订阅处理失败 subNo={}: {}", subscription.getSubNo(), e.toString(), e);
            }
        }
    }

    /**
     * 补发停机期间错过的周期：逐期生成并把游标前移，直到追上当前时间。
     *
     * <p>停得太久（超过保留期数）时不再逐期补，直接跳到当前时间之后——
     * 补发几十封历史邮件不是订阅人要的，保留期数以外的报告也不再对外展示（AC-B5-7）。
     */
    private void catchUp(PlmSubscription subscription, LocalDateTime now) {
        int retain = Math.max(1, properties.getReportRetain());
        int generated = 0;
        while (subscription.getNextRunAt() != null && !subscription.getNextRunAt().isAfter(now)) {
            if (generated >= retain) {
                LocalDateTime skippedTo = subscriptionService.nextRunAt(subscription, now);
                log.warn("订阅 {} 积压超过 {} 期，游标由 {} 直接前移至 {}",
                        subscription.getSubNo(), retain, subscription.getNextRunAt(), skippedTo);
                subscription.setNextRunAt(skippedTo);
                subscriptionMapper.updateById(subscription);
                return;
            }
            LocalDateTime runAt = subscription.getNextRunAt();
            try {
                reportService.generatePeriod(subscription, runAt);
            } catch (Exception e) {
                log.error("周期报告生成异常 subNo={} runAt={}: {}", subscription.getSubNo(), runAt, e.toString(), e);
            }
            generated++;
            subscription.setNextRunAt(subscriptionService.nextRunAt(subscription, runAt));
            subscriptionMapper.updateById(subscription);
        }
    }
}
