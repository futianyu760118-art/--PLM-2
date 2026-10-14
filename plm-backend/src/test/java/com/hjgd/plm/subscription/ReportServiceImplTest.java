package com.hjgd.plm.subscription;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.spi.LedgerChangeEvent;
import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.ReportDetailVO;
import com.hjgd.plm.subscription.dto.ReportVO;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.entity.PlmReport;
import com.hjgd.plm.subscription.entity.PlmReportDeliveryLog;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.subscription.mapper.PlmReportDeliveryLogMapper;
import com.hjgd.plm.subscription.mapper.PlmReportMapper;
import com.hjgd.plm.subscription.mapper.ReportItemMapper;
import com.hjgd.plm.subscription.service.SubscriptionService;
import com.hjgd.plm.subscription.service.impl.ReportServiceImpl;
import com.hjgd.plm.subscription.service.impl.RepositoryItemAssembler;
import com.hjgd.plm.subscription.spi.ReportChannelSender;
import com.hjgd.plm.subscription.support.ReportExcelWriter;
import com.hjgd.plm.subscription.support.ReportHtmlBuilder;
import com.hjgd.plm.subscription.support.UserScopeResolver;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 报告生成与推送（能力点 B5）：AC-B5-1/B5-2/B5-3/B5-5/B5-6/B5-7/B5-8、
 * 以及 B3-4「同一聚合窗口只出一份单条报告」。
 */
@DisplayName("报告生成与推送（能力线 B）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    @Mock private PlmReportMapper reportMapper;
    @Mock private PlmReportDeliveryLogMapper deliveryLogMapper;
    @Mock private ReportItemMapper reportItemMapper;
    @Mock private RepositoryItemAssembler assembler;
    @Mock private UserScopeResolver scopeResolver;
    @Mock private ReportHtmlBuilder htmlBuilder;
    @Mock private ReportExcelWriter excelWriter;
    @Mock private SubscriptionService subscriptionService;
    @Mock private SequenceService sequenceService;
    @Mock private LedgerNotifyService notifyService;
    @Mock private PlmLedgerMapper ledgerMapper;

    @Mock private ReportChannelSender inboxSender;
    @Mock private ReportChannelSender emailSender;

    private SubscriptionProperties properties;
    private ReportServiceImpl service;
    private final List<PlmReport> inserted = new ArrayList<>();
    private long nextId = 1L;

    @BeforeEach
    void setUp() {
        properties = SubscriptionTestSupport.props();
        SubscriptionTestSupport.login("report:view", "report:download");

        when(inboxSender.channel()).thenReturn(ReportChannelSender.CHANNEL_INBOX);
        when(emailSender.channel()).thenReturn(ReportChannelSender.CHANNEL_EMAIL);
        when(inboxSender.send(any(), any(), any()))
                .thenReturn(ReportChannelSender.Delivery.success("站内通知"));
        when(emailSender.send(any(), any(), any()))
                .thenReturn(ReportChannelSender.Delivery.success("zhangsan@hjgd.com"));

        // 报告行落库时补一个 id，后续 selectById（重新读回状态）才拿得到同一行
        when(reportMapper.insert(any(PlmReport.class))).thenAnswer(inv -> {
            PlmReport r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(nextId++);
            }
            inserted.add(r);
            return 1;
        });
        when(reportMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return inserted.stream().filter(r -> id != null && id.equals(r.getId())).findFirst().orElse(null);
        });
        when(sequenceService.nextNo("REPORT_NO")).thenReturn("RPT-20261014-0001");
        when(scopeResolver.resolve(anyLong()))
                .thenReturn(new UserScopeResolver.Scope(SubscriptionTestSupport.DEPT_ID, SubscriptionTestSupport.USER_ID, false));
    }

    @AfterEach
    void tearDown() {
        SubscriptionTestSupport.logout();
    }

    private ReportServiceImpl build(ReportChannelSender... senders) {
        return new ReportServiceImpl(reportMapper, deliveryLogMapper, reportItemMapper, assembler,
                scopeResolver, htmlBuilder, excelWriter, subscriptionService, properties,
                sequenceService, notifyService, ledgerMapper, new ObjectMapper(), List.of(senders));
    }

    private PlmLedger ledger() {
        PlmLedger l = new PlmLedger();
        l.setId(100L);
        l.setLedgerNo("EV-20261014-0001");
        l.setName("投光灯检验报告");
        l.setType("INSPECT_REPORT");
        return l;
    }

    private RepositoryItemVO item() {
        RepositoryItemVO vo = new RepositoryItemVO();
        vo.setLedgerId(100L);
        vo.setLedgerNo("EV-20261014-0001");
        vo.setName("投光灯检验报告");
        vo.setLink("/ledger/repository?ledgerId=100");
        return vo;
    }

    // ===================== 周期报告（AC-B4/B5） =====================

    @Test
    @DisplayName("AC-B5-2/B5-6 周期报告落库并推送：正文带链接、投递逐渠道留痕")
    void periodReportPushesWithDeliveryLogs() {
        service = build(inboxSender, emailSender);
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt))
                .thenReturn(new LocalDateTime[]{LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(ledger()));
        when(assembler.assemble(any(), anyBoolean())).thenReturn(List.of(item()));
        when(htmlBuilder.buildPeriod(any(), any(), any(), any())).thenReturn("<div>本期</div>");
        when(excelWriter.write(any(), any())).thenReturn(new byte[]{1, 2, 3});

        ReportVO vo = service.generatePeriod(sub, runAt);

        assertEquals("RPT-20261014-0001", vo.getReportNo());
        assertEquals("PUSHED", vo.getStatus());
        assertEquals(1, vo.getItemCount());
        assertTrue(vo.getHasExcel(), "有内容就有可下载的 Excel");

        ArgumentCaptor<PlmReport> reportCap = ArgumentCaptor.forClass(PlmReport.class);
        verify(reportMapper).insert(reportCap.capture());
        PlmReport saved = reportCap.getValue();
        assertEquals("每日汇总", saved.getSubName(), "订阅名称按快照落库（AC-B4-8）");
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 0), saved.getPeriodStart());
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), saved.getPeriodEnd());
        assertTrue(saved.getContentJson().contains("EV-20261014-0001"));
        assertEquals("<div>本期</div>", saved.getHtmlBody());

        ArgumentCaptor<PlmReportDeliveryLog> logCap = ArgumentCaptor.forClass(PlmReportDeliveryLog.class);
        verify(deliveryLogMapper, times(2)).insert(logCap.capture());
        List<String> channels = logCap.getAllValues().stream().map(PlmReportDeliveryLog::getChannel).toList();
        assertTrue(channels.contains(ReportChannelSender.CHANNEL_INBOX));
        assertTrue(channels.contains(ReportChannelSender.CHANNEL_EMAIL));
    }

    @Test
    @DisplayName("AC-B5-5 取数重试耗尽 → 落 FAILED 报告并通知订阅人，且「已告知」也留痕")
    void exhaustedRetryRecordsFailureAndNotifies() {
        properties.setReportRetry(3);
        service = build(inboxSender);
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt)).thenReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenThrow(new IllegalStateException("连接池耗尽"));

        ReportVO vo = service.generatePeriod(sub, runAt);

        verify(reportItemMapper, times(3)).selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any());
        assertEquals("FAILED", vo.getStatus());
        assertEquals(3, vo.getRetryCount());
        assertTrue(vo.getError().contains("连接池耗尽"));
        assertEquals(0, vo.getItemCount());

        ArgumentCaptor<PlmReport> cap = ArgumentCaptor.forClass(PlmReport.class);
        verify(reportMapper).insert(cap.capture());
        assertEquals("FAILED", cap.getValue().getStatus());
        assertEquals("[]", cap.getValue().getContentJson());

        // 通知订阅人 + 该次「已告知」同样落一条 INBOX 成功日志（AC-B5-6 成功失败均记）
        verify(notifyService).notify(eq(SubscriptionTestSupport.USER_ID),
                eq(LedgerNotifyService.TYPE_SUB_REPORT), eq("报告生成失败"),
                eq("REPORT"), any(), any(), any());
        ArgumentCaptor<PlmReportDeliveryLog> logCap = ArgumentCaptor.forClass(PlmReportDeliveryLog.class);
        verify(deliveryLogMapper).insert(logCap.capture());
        assertEquals(ReportChannelSender.CHANNEL_INBOX, logCap.getValue().getChannel());
        assertEquals(ReportChannelSender.RESULT_SUCCESS, logCap.getValue().getResult());
    }

    @Test
    @DisplayName("重试后成功：retryCount 记录实际重试次数，报告仍正常推送")
    void retrySucceedsOnSecondAttempt() {
        properties.setReportRetry(3);
        service = build(inboxSender);
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt)).thenReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenThrow(new IllegalStateException("瞬时失败"))
                .thenReturn(List.of(ledger()));
        when(assembler.assemble(any(), anyBoolean())).thenReturn(List.of(item()));
        when(htmlBuilder.buildPeriod(any(), any(), any(), any())).thenReturn("<div>本期</div>");

        ReportVO vo = service.generatePeriod(sub, runAt);

        assertEquals("PUSHED", vo.getStatus());
        assertEquals(1, vo.getRetryCount(), "第 2 次才成功 = 重试 1 次");
        assertEquals(1, vo.getItemCount());
    }

    @Test
    @DisplayName("AC-B5-5 渠道全挂 → 报告转 FAILED 并通知订阅人")
    void allChannelsFailedMarksReportFailed() {
        service = build(inboxSender);
        when(inboxSender.send(any(), any(), any()))
                .thenReturn(ReportChannelSender.Delivery.failed("站内通知", "通知服务不可用"));
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt)).thenReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(ledger()));
        when(assembler.assemble(any(), anyBoolean())).thenReturn(List.of(item()));
        when(htmlBuilder.buildPeriod(any(), any(), any(), any())).thenReturn("<div>本期</div>");

        ReportVO vo = service.generatePeriod(sub, runAt);

        assertEquals("FAILED", vo.getStatus());
        assertEquals("全部推送渠道失败", vo.getError());
        verify(notifyService).notify(eq(SubscriptionTestSupport.USER_ID),
                eq(LedgerNotifyService.TYPE_SUB_REPORT), eq("报告推送失败"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-B5-6 渠道抛异常只影响该渠道，其它渠道照常投递")
    void oneChannelCrashDoesNotBlockOthers() {
        service = build(inboxSender, emailSender);
        when(inboxSender.send(any(), any(), any())).thenThrow(new RuntimeException("NPE in inbox"));
        when(emailSender.send(any(), any(), any()))
                .thenReturn(ReportChannelSender.Delivery.success("zhangsan@hjgd.com"));
        when(excelWriter.write(any(), any())).thenReturn(new byte[]{9});
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt)).thenReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(ledger()));
        when(assembler.assemble(any(), anyBoolean())).thenReturn(List.of(item()));
        when(htmlBuilder.buildPeriod(any(), any(), any(), any())).thenReturn("<div>本期</div>");

        ReportVO vo = service.generatePeriod(sub, runAt);

        assertEquals("PUSHED", vo.getStatus(), "有渠道成功即视为已推送");
        ArgumentCaptor<PlmReportDeliveryLog> cap = ArgumentCaptor.forClass(PlmReportDeliveryLog.class);
        verify(deliveryLogMapper, times(2)).insert(cap.capture());
        assertTrue(cap.getAllValues().stream()
                        .anyMatch(l -> ReportChannelSender.RESULT_FAILED.equals(l.getResult())),
                "炸掉的渠道必须留失败痕");
    }

    @Test
    @DisplayName("本期无更新仍出报告（AC-B4-5），明细为空而不是不发")
    void emptyPeriodStillProducesReport() {
        service = build(inboxSender);
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime runAt = LocalDateTime.of(2026, 10, 14, 8, 0);
        when(subscriptionService.periodOf(sub, runAt)).thenReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 14, 0, 0)});
        when(reportItemMapper.selectPeriodItems(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        when(assembler.assemble(any(), anyBoolean())).thenReturn(List.of());
        when(htmlBuilder.buildPeriod(any(), any(), any(), any())).thenReturn("<div>本期无更新。</div>");

        ReportVO vo = service.generatePeriod(sub, runAt);

        assertEquals(0, vo.getItemCount());
        assertEquals("PUSHED", vo.getStatus());
        assertEquals(0, vo.getRetryCount());
    }

    // ===================== 单条报告（AC-B3-4） =====================

    @Test
    @DisplayName("AC-B3-4 单条报告的站内通知由变更推送承担，报告侧记「已跳过」并仍生成 Excel")
    void itemReportSkipsInboxButKeepsArchive() {
        service = build(inboxSender, emailSender);
        when(excelWriter.write(any(), any())).thenReturn(new byte[]{7});
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        sub.setType("ITEM");
        sub.setLedgerId(100L);
        LedgerChangeEvent event = LedgerChangeEvent.builder()
                .kind(LedgerChangeEvent.Kind.UPDATED)
                .ledgerId(100L).ledgerNo("EV-20261014-0001").ledgerName("投光灯检验报告")
                .operatorId(200L).operatorName("李四")
                .occurredAt(LocalDateTime.of(2026, 10, 14, 10, 30))
                .changes(List.of()).summary("备注变更")
                .build();
        when(ledgerMapper.selectById(100L)).thenReturn(null); // 行已删：仍要给出条目
        when(htmlBuilder.buildItem(any(), any(), any(), any())).thenReturn("<div>单条</div>");

        ReportVO vo = service.generateItem(sub, event);

        assertEquals("ITEM", vo.getType());
        assertEquals(1, vo.getItemCount());
        assertEquals(LocalDateTime.of(2026, 10, 14, 10, 30), vo.getPeriodStart(), "单条报告区间即变更发生那一刻");
        assertEquals(vo.getPeriodStart(), vo.getPeriodEnd());

        verify(inboxSender, never()).send(any(), any(), any());
        verify(emailSender, times(1)).send(any(), any(), any());

        ArgumentCaptor<PlmReportDeliveryLog> cap = ArgumentCaptor.forClass(PlmReportDeliveryLog.class);
        verify(deliveryLogMapper, times(2)).insert(cap.capture());
        PlmReportDeliveryLog inboxLog = cap.getAllValues().stream()
                .filter(l -> ReportChannelSender.CHANNEL_INBOX.equals(l.getChannel())).findFirst().orElseThrow();
        assertEquals(ReportChannelSender.RESULT_SKIPPED, inboxLog.getResult());
        assertTrue(inboxLog.getError().contains("变更推送"));
    }

    @Test
    @DisplayName("AC-B3-4 同窗口内已有单条报告 → 不再重复生成（跨窗口放行）")
    void itemReportDedupWithinWindow() {
        service = build(inboxSender);
        when(reportMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.itemReportExistsSince(1L, LocalDateTime.now().minusMinutes(1)));

        when(reportMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.itemReportExistsSince(1L, LocalDateTime.now().minusMinutes(1)));
        assertFalse(service.itemReportExistsSince(null, LocalDateTime.now()));
        assertFalse(service.itemReportExistsSince(1L, null));
    }

    // ===================== 回溯与权限（AC-B5-3/B5-7） =====================

    @Test
    @DisplayName("AC-B5-7 列表只回溯最近 12 期：请求 100 条也被压到保留期数")
    @SuppressWarnings("unchecked")
    void pageCapsToRetain() {
        properties.setReportRetain(12);
        service = build(inboxSender);
        when(reportMapper.selectPage(any(), any())).thenReturn(emptyPage());

        service.page(1, 100, null);

        ArgumentCaptor<IPage<PlmReport>> cap = ArgumentCaptor.forClass(IPage.class);
        verify(reportMapper).selectPage(cap.capture(), any());
        assertEquals(12, cap.getValue().getSize());
    }

    @SuppressWarnings("unchecked")
    private IPage<PlmReport> emptyPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 12);
    }

    @Test
    @DisplayName("AC-B5-3 非订阅人拿不到报告内容（详情/正文/Excel 同一处校验）")
    void foreignReportIsForbidden() {
        service = build(inboxSender);
        PlmReport other = new PlmReport();
        other.setId(1L);
        other.setUserId(999L);
        other.setContentJson("[]");
        when(reportMapper.selectById(1L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.detail(1L));
        assertThrows(BusinessException.class, () -> service.htmlOf(1L));
        assertThrows(BusinessException.class, () -> service.excelOf(1L));
    }

    @Test
    @DisplayName("不存在的报告 = NOT_FOUND，与「无权限」区分开")
    void missingReportIsNotFound() {
        service = build(inboxSender);
        when(reportMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detail(404L));
    }

    @Test
    @DisplayName("AC-B5-6 详情返回头条 + 明细 + 投递记录（含订阅编号与周期区间可回溯）")
    void detailCarriesItemsAndDeliveries() {
        service = build(inboxSender);
        PlmReport report = new PlmReport();
        report.setId(1L);
        report.setUserId(SubscriptionTestSupport.USER_ID);
        report.setReportNo("RPT-20261014-0001");
        report.setSubscriptionId(5L);
        report.setSubName("每日汇总");
        report.setType("PERIOD");
        report.setContentJson("[{\"ledgerId\":100,\"ledgerNo\":\"EV-20261014-0001\",\"name\":\"投光灯检验报告\"}]");
        when(reportMapper.selectById(1L)).thenReturn(report);
        PlmReportDeliveryLog log = new PlmReportDeliveryLog();
        log.setChannel(ReportChannelSender.CHANNEL_INBOX);
        log.setResult(ReportChannelSender.RESULT_SUCCESS);
        when(deliveryLogMapper.selectList(any())).thenReturn(List.of(log));

        ReportDetailVO vo = service.detail(1L);

        assertEquals(1L, vo.getReport().getId());
        assertEquals(5L, vo.getReport().getSubscriptionId());
        assertEquals(1, vo.getItems().size());
        assertEquals("EV-20261014-0001", vo.getItems().get(0).getLedgerNo());
        assertEquals(1, vo.getDeliveries().size());
        assertEquals(ReportChannelSender.RESULT_SUCCESS, vo.getDeliveries().get(0).getResult());
    }

    @Test
    @DisplayName("contentJson 损坏时降级为空明细，报告头仍可打开")
    void brokenContentJsonDegradesToEmptyItems() {
        service = build(inboxSender);
        PlmReport report = new PlmReport();
        report.setId(1L);
        report.setUserId(SubscriptionTestSupport.USER_ID);
        report.setContentJson("{不是合法 JSON");
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(deliveryLogMapper.selectList(any())).thenReturn(List.of());

        ReportDetailVO vo = service.detail(1L);

        assertTrue(vo.getItems().isEmpty());
        assertEquals(1L, vo.getReport().getId());
    }
}
