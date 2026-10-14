package com.hjgd.plm.subscription.spi;

import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.entity.PlmReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知渠道（AC-B5 渠道之一）。
 *
 * <p>复用能力线 A 的通知底座，因此天然继承两件事：用户在通知设置里对
 * {@code SUB_REPORT} 类型的开关（AC-A4-11），以及按 {@code 用户+类型+对象+5 分钟窗口} 的聚合。
 * 聚合键刻意带上报告 id——否则同一用户 5 分钟内收到的两份不同报告会被并成一条，
 * 第二份就看不见了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxReportSender implements ReportChannelSender {

    private final LedgerNotifyService notifyService;

    @Override
    public String channel() {
        return CHANNEL_INBOX;
    }

    @Override
    public Delivery send(PlmReport report, List<RepositoryItemVO> items, byte[] excel) {
        try {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("reportId", report.getId());
            extra.put("reportNo", report.getReportNo());
            extra.put("subName", report.getSubName());
            extra.put("itemCount", report.getItemCount());
            extra.put("periodStart", report.getPeriodStart() == null ? null : report.getPeriodStart().toString());
            extra.put("periodEnd", report.getPeriodEnd() == null ? null : report.getPeriodEnd().toString());
            extra.put("reportLink", "/subscription/report?id=" + report.getId());
            if (items.isEmpty()) {
                // AC-B4-5：本期无更新也要发，让订阅人能确认订阅仍然有效
                extra.put("summary", "本期无更新");
            }
            String title = report.getItemCount() == 0
                    ? "【" + nz(report.getSubName()) + "】本期无更新"
                    : "【" + nz(report.getSubName()) + "】本期更新 " + report.getItemCount() + " 条资料";
            notifyService.notify(report.getUserId(), LedgerNotifyService.TYPE_SUB_REPORT,
                    title, "REPORT", String.valueOf(report.getId()), List.of(), extra);
            return Delivery.success("站内通知");
        } catch (Exception e) {
            log.warn("报告站内推送失败 reportNo={}: {}", report.getReportNo(), e.getMessage());
            return Delivery.failed("站内通知", e.getMessage());
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
