package com.hjgd.plm.subscription;

import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.spi.LedgerChangeEvent;
import com.hjgd.plm.subscription.dto.ReportVO;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.subscription.service.ReportService;
import com.hjgd.plm.subscription.service.SubscriptionService;
import com.hjgd.plm.subscription.spi.ItemSubscriptionNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 单条订阅推送（B3 的推送侧）：AC-B3-2/B3-3/B3-4/B3-6/B3-7，
 * 以及「监听方异常不得冒泡拖垮台账写入」的约定。
 */
@DisplayName("单条订阅推送（能力线 B）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemSubscriptionNotifierTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private ReportService reportService;
    @Mock private LedgerNotifyService notifyService;

    private ItemSubscriptionNotifier notifier;

    @BeforeEach
    void setUp() {
        LedgerProperties ledgerProperties = new LedgerProperties();
        ledgerProperties.setNotifyAggregateMinutes(5);
        notifier = new ItemSubscriptionNotifier(subscriptionService, reportService, notifyService, ledgerProperties);
    }

    private PlmSubscription subscriber(Long userId) {
        PlmSubscription sub = new PlmSubscription();
        sub.setId(1L);
        sub.setSubNo("SUB-20261014-0001");
        sub.setUserId(userId);
        sub.setType("ITEM");
        sub.setLedgerId(100L);
        sub.setStatus("ENABLED");
        return sub;
    }

    private LedgerChangeEvent event(LedgerChangeEvent.Kind kind, String status, Long operatorId) {
        return LedgerChangeEvent.builder()
                .kind(kind)
                .ledgerId(100L)
                .ledgerNo("EV-20261014-0001")
                .ledgerName("投光灯检验报告")
                .ledgerType("INSPECT_REPORT")
                .status(status)
                .ownerId(SubscriptionTestSupport.USER_ID)
                .deptId(SubscriptionTestSupport.DEPT_ID)
                .operatorId(operatorId)
                .operatorName("李四")
                .occurredAt(LocalDateTime.of(2026, 10, 14, 10, 0))
                .changes(List.of(Map.of("field", "remark", "before", "旧", "after", "新")))
                .build();
    }

    @Test
    @DisplayName("AC-B3-3 变更推送给订阅人：带前后值，且 extra 里带指向资料汇总处的链接")
    void notifiesSubscriberWithLink() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(false);
        ReportVO report = new ReportVO();
        report.setReportNo("RPT-20261014-0001");
        when(reportService.generateItem(any(), any())).thenReturn(report);

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED", 999L));

        ArgumentCaptor<Map<String, Object>> extraCap = ArgumentCaptor.forClass(Map.class);
        verify(notifyService).notify(eq(200L), eq(LedgerNotifyService.TYPE_SUB_ITEM), any(),
                eq("LEDGER"), eq("EV-20261014-0001"), any(), extraCap.capture());
        assertEquals("/ledger/repository?ledgerId=100", extraCap.getValue().get("link"));
        assertEquals("EV-20261014-0001", extraCap.getValue().get("ledgerNo"));
        assertEquals("李四", extraCap.getValue().get("operator"));
        verify(reportService).generateItem(any(), any());
    }

    @Test
    @DisplayName("AC-B3-6 操作人自己改的资料不通知自己，但仍为其它订阅人推送")
    void skipsSelfButKeepsOthers() {
        PlmSubscription mine = subscriber(SubscriptionTestSupport.USER_ID);
        PlmSubscription other = subscriber(200L);
        other.setId(2L);
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(mine, other));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(false);
        when(reportService.generateItem(any(), any())).thenReturn(new ReportVO());

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED",
                SubscriptionTestSupport.USER_ID));

        verify(notifyService, times(1)).notify(any(), any(), any(), any(), any(), any(), any());
        verify(notifyService).notify(eq(200L), any(), any(), any(), any(), any(), any());
        verify(reportService, times(1)).generateItem(any(), any());
    }

    @Test
    @DisplayName("AC-B3-4 同一聚合窗口内已有单条报告 → 不再重复生成报告（站内通知照常聚合）")
    void windowAggregationSkipsSecondReport() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(true);

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED", 999L));

        verify(reportService, never()).generateItem(any(), any());
        verify(notifyService).notify(eq(200L), any(), any(), any(), any(), any(), any());
        verify(subscriptionService, never()).invalidateItemSubscriptions(anyLong());
    }

    @Test
    @DisplayName("AC-B3-7 资料作废/删除：推一次之后订阅自动失效")
    void voidEventInvalidatesSubscription() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(false);
        when(reportService.generateItem(any(), any())).thenReturn(new ReportVO());

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "VOID", 999L));

        verify(notifyService).notify(eq(200L), eq(LedgerNotifyService.TYPE_SUB_ITEM),
                org.mockito.ArgumentMatchers.contains("已作废"), any(), any(), any(), any());
        verify(subscriptionService).invalidateItemSubscriptions(100L);
    }

    @Test
    @DisplayName("AC-B3-7 删除事件的标题与作废区分开")
    void deletedEventHasItsOwnTitle() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(false);
        when(reportService.generateItem(any(), any())).thenReturn(new ReportVO());

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.DELETED, null, 999L));

        verify(notifyService).notify(eq(200L), eq(LedgerNotifyService.TYPE_SUB_ITEM),
                org.mockito.ArgumentMatchers.contains("已删除"), any(), any(), any(), any());
        verify(subscriptionService).invalidateItemSubscriptions(100L);
    }

    @Test
    @DisplayName("AC-B3-2 附件追加/移除走同一条推送路径，标题点明是附件变更")
    void attachmentChangeIsPushed() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(true);

        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.ATTACH_ADDED, "SUBMITTED", 999L));
        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.ATTACH_REMOVED, "SUBMITTED", 999L));

        verify(notifyService).notify(eq(200L), eq(LedgerNotifyService.TYPE_SUB_ITEM),
                org.mockito.ArgumentMatchers.contains("新增了附件"), any(), any(), any(), any());
        verify(notifyService).notify(eq(200L), eq(LedgerNotifyService.TYPE_SUB_ITEM),
                org.mockito.ArgumentMatchers.contains("移除了附件"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("单个订阅人推送失败不影响其它订阅人（逐个 try/catch）")
    void oneSubscriberFailureDoesNotBlockOthers() {
        PlmSubscription a = subscriber(200L);
        PlmSubscription b = subscriber(201L);
        b.setId(2L);
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(a, b));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(true);
        doThrow(new RuntimeException("站内通知写库失败"))
                .when(notifyService).notify(eq(200L), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED", 999L)));

        verify(notifyService).notify(eq(201L), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("查订阅人失败时自行吞掉：不能把一次正常的台账写入回滚掉")
    void subscriberLookupFailureIsSwallowed() {
        when(subscriptionService.subscribersOf(100L)).thenThrow(new RuntimeException("DB 抖动"));

        assertDoesNotThrow(() ->
                notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED", 999L)));
    }

    @Test
    @DisplayName("订阅失效失败时同样自行吞掉：作废本身已经成功，不能反过来报错")
    void invalidationFailureIsSwallowed() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of(subscriber(200L)));
        when(reportService.itemReportExistsSince(anyLong(), any())).thenReturn(true);
        doThrow(new RuntimeException("失效失败"))
                .when(subscriptionService).invalidateItemSubscriptions(anyLong());

        assertDoesNotThrow(() ->
                notifier.onLedgerChange(event(LedgerChangeEvent.Kind.DELETED, null, 999L)));
    }

    @Test
    @DisplayName("无 ledgerId / null 事件直接忽略")
    void ignoresEmptyEvents() {
        notifier.onLedgerChange(null);
        notifier.onLedgerChange(LedgerChangeEvent.builder().kind(LedgerChangeEvent.Kind.UPDATED).build());
        verify(subscriptionService, never()).subscribersOf(anyLong());
    }

    @Test
    @DisplayName("无人订阅时不产生任何通知与报告")
    void noSubscribersNoop() {
        when(subscriptionService.subscribersOf(100L)).thenReturn(List.of());
        notifier.onLedgerChange(event(LedgerChangeEvent.Kind.UPDATED, "SUBMITTED", 999L));
        verify(notifyService, never()).notify(any(), any(), any(), any(), any(), any(), any());
        verify(reportService, never()).generateItem(any(), any());
    }
}
