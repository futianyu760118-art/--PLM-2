package com.hjgd.plm.subscription.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ledger.spi.LedgerChangeEvent;
import com.hjgd.plm.subscription.dto.ReportDetailVO;
import com.hjgd.plm.subscription.dto.ReportVO;
import com.hjgd.plm.subscription.entity.PlmSubscription;

import java.time.LocalDateTime;

/**
 * 报告生成、推送与回溯（能力点 B5，含 B3 单条订阅的报告留档）。
 *
 * <p>两种报告共用一张表与一套投递流程，差别只在「资料范围怎么来」：
 * <ul>
 *   <li>周期报告 —— 调度到点触发，按订阅的分类条件取上一段自然周期内的新增/变更（AC-B4-3）；</li>
 *   <li>单条报告 —— 台账变更事件触发，范围就是那一条（AC-B3-7 / AC-B5-8）。</li>
 * </ul>
 *
 * <p>报告行落库后即使投递全部失败也不删除：AC-B5-6 要求成功与失败都留痕，
 * 「生成过但没发出去」本身就是要被查出来的事实。
 */
public interface ReportService {

    /** 我的报告（AC-B5-7 最近 12 期可回溯） */
    PageResult<ReportVO> page(Integer pageNum, Integer pageSize, String type);

    /** 报告详情：报告头 + 资料明细 + 渠道投递记录 */
    ReportDetailVO detail(Long id);

    /** 报告 Excel 明细字节流（AC-B5-7 下载） */
    byte[] excelOf(Long id);

    /** 报告 HTML 正文（站内通知与邮件共用同一份） */
    String htmlOf(Long id);

    /** 生成并推送一次周期报告（AC-B4-3/B4-5、AC-B5） */
    ReportVO generatePeriod(PlmSubscription subscription, LocalDateTime runAt);

    /** 生成并推送一条单条订阅报告（AC-B3-3/B3-7） */
    ReportVO generateItem(PlmSubscription subscription, LedgerChangeEvent event);

    /**
     * 聚合窗口内是否已为这条订阅发过单条报告（AC-B3-4）。
     *
     * <p>站内通知靠 {@code agg_key} 天然聚合，邮件与报告快照没有这层机制；
     * 调用方用本方法在同一窗口内只放行第一份，避免高频被改的资料把订阅人的邮箱刷屏。
     */
    boolean itemReportExistsSince(Long subscriptionId, LocalDateTime since);
}
