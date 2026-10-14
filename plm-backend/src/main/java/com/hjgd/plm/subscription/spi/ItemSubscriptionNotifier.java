package com.hjgd.plm.subscription.spi;

import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.spi.LedgerChangeEvent;
import com.hjgd.plm.ledger.spi.LedgerChangeListener;
import com.hjgd.plm.subscription.dto.ReportVO;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.subscription.service.ReportService;
import com.hjgd.plm.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条资料订阅的推送方（能力点 B3）。
 *
 * <p>为什么是监听台账写事件而不是轮询台账表：附件的追加/移除既不改台账行的时间戳、
 * 也不改任何台账字段，轮询看不见这两类变更；而监听发生在写事务内，
 * 能拿到「改了哪个字段、改前改后各是什么」（AC-B3-3 的前后值要求）。
 *
 * <p>本类的任何异常都必须自行吞掉（见 {@link LedgerChangeListener} 约定）：
 * 推送失败不能把一次正常的台账写入回滚掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemSubscriptionNotifier implements LedgerChangeListener {

    private final SubscriptionService subscriptionService;
    private final ReportService reportService;
    private final LedgerNotifyService notifyService;
    private final LedgerProperties ledgerProperties;

    @Override
    public void onLedgerChange(LedgerChangeEvent event) {
        if (event == null || event.getLedgerId() == null) {
            return;
        }
        try {
            List<PlmSubscription> subscriptions = subscriptionService.subscribersOf(event.getLedgerId());
            String title = titleOf(event);
            // 聚合窗口起点：与通知聚合用同一口径的绝对时间桶，而不是「往前推 N 分钟」，
            // 否则同一窗口内先后到达的两次变更会被判成两个不同的窗口
            LocalDateTime windowStart = windowStart();
            for (PlmSubscription subscription : subscriptions) {
                try {
                    pushOne(subscription, event, title, windowStart);
                } catch (Exception e) {
                    // 一个订阅人推送失败不影响其它订阅人
                    log.warn("单条订阅推送失败 subNo={} ledgerNo={}: {}",
                            subscription.getSubNo(), event.getLedgerNo(), e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("单条订阅推送处理失败 ledgerId={}: {}", event.getLedgerId(), e.toString());
        }
        if (event.isVoidOrDelete()) {
            // AC-B3-7：删除/作废推一次之后订阅自动失效，避免用户继续等一条已经没有的资料的消息
            try {
                subscriptionService.invalidateItemSubscriptions(event.getLedgerId());
            } catch (Exception e) {
                log.warn("失效单条订阅失败 ledgerId={}: {}", event.getLedgerId(), e.toString());
            }
        }
    }

    private void pushOne(PlmSubscription subscription, LedgerChangeEvent event, String title,
                         LocalDateTime windowStart) {
        // 操作人自己改的资料不必通知自己
        if (event.getOperatorId() != null && event.getOperatorId().equals(subscription.getUserId())) {
            return;
        }
        // AC-B3-4：同一窗口内站内通知会聚合，邮件与报告快照则只放行第一份
        if (!reportService.itemReportExistsSince(subscription.getId(), windowStart)) {
            ReportVO report = reportService.generateItem(subscription, event);
            log.debug("单条订阅报告已生成 subNo={} reportNo={}", subscription.getSubNo(), report.getReportNo());
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ledgerNo", event.getLedgerNo());
        extra.put("ledgerName", event.getLedgerName());
        extra.put("operator", event.getOperatorName());
        // AC-B3-3：通知里带指向资料汇总处该条资料的链接，点一次直达（AC-B5-4）
        extra.put("link", ledgerLink(event));
        notifyService.notify(subscription.getUserId(), LedgerNotifyService.TYPE_SUB_ITEM, title,
                "LEDGER", event.getLedgerNo(), event.getChanges(), extra);
    }

    private String ledgerLink(LedgerChangeEvent event) {
        return "/ledger/repository?ledgerId=" + event.getLedgerId();
    }

    private String titleOf(LedgerChangeEvent event) {
        String name = StringUtils.hasText(event.getLedgerName()) ? event.getLedgerName() : event.getLedgerNo();
        if (event.getKind() == LedgerChangeEvent.Kind.DELETED) {
            return "订阅资料「" + name + "」已删除，订阅自动失效";
        }
        if (event.isVoidOrDelete()) {
            return "订阅资料「" + name + "」已作废，订阅自动失效";
        }
        return switch (event.getKind()) {
            case ATTACH_ADDED -> "订阅资料「" + name + "」新增了附件";
            case ATTACH_REMOVED -> "订阅资料「" + name + "」移除了附件";
            default -> "订阅资料「" + name + "」发生变更";
        };
    }

    private LocalDateTime windowStart() {
        long windowMs = Math.max(1, ledgerProperties.getNotifyAggregateMinutes()) * 60_000L;
        long bucket = (System.currentTimeMillis() / windowMs) * windowMs;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(bucket), ZoneId.systemDefault());
    }
}
