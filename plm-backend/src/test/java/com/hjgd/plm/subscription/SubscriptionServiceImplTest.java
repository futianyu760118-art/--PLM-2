package com.hjgd.plm.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.ledger.dto.CategoryFilterGroup;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.SubscriptionRequest;
import com.hjgd.plm.subscription.dto.SubscriptionVO;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.subscription.mapper.PlmSubscriptionMapper;
import com.hjgd.plm.subscription.service.CategoryService;
import com.hjgd.plm.subscription.service.SubscriptionService;
import com.hjgd.plm.subscription.service.impl.SubscriptionServiceImpl;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 订阅（能力点 B3 单条 / B4 周期）：AC-B3-1/B3-5/B3-6/B3-8、
 * AC-B4-1/B4-2/B4-5/B4-6/B4-7/B4-8/B4-9。
 */
@DisplayName("订阅管理（能力线 B）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceImplTest {

    @Mock private PlmSubscriptionMapper subscriptionMapper;
    @Mock private PlmLedgerMapper ledgerMapper;
    @Mock private LedgerService ledgerService;
    @Mock private CategoryService categoryService;
    @Mock private SequenceService sequenceService;

    private SubscriptionProperties properties;
    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = SubscriptionTestSupport.props();
        service = new SubscriptionServiceImpl(subscriptionMapper, ledgerMapper, ledgerService,
                categoryService, properties, sequenceService, new ObjectMapper());
        SubscriptionTestSupport.login("subscription:view", "subscription:manage");
        when(sequenceService.nextNo("SUBSCRIPTION_NO")).thenReturn("SUB-20261014-0001");
        when(categoryService.describeFilter(any())).thenReturn("本部门全部资料");
    }

    @AfterEach
    void tearDown() {
        SubscriptionTestSupport.logout();
    }

    // ===================== 生成时点（AC-B4-1/B4-7） =====================

    @Test
    @DisplayName("AC-B4-1 按日：当日时点未到取当日，已过取次日")
    void nextRunAtDaily() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);

        assertEquals(LocalDateTime.of(2026, 10, 14, 8, 0),
                service.nextRunAt(sub, LocalDateTime.of(2026, 10, 14, 7, 0)));
        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 0),
                service.nextRunAt(sub, LocalDateTime.of(2026, 10, 14, 9, 0)));
    }

    @Test
    @DisplayName("AC-B4-1 按周：命中本周星期几，已过则顺延一周")
    void nextRunAtWeekly() {
        // 2026-10-14 是周三
        assertEquals(DayOfWeek.WEDNESDAY, LocalDate.of(2026, 10, 14).getDayOfWeek());
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "WEEK", 3, null);

        LocalDateTime same = service.nextRunAt(sub, LocalDateTime.of(2026, 10, 14, 7, 0));
        assertEquals(LocalDateTime.of(2026, 10, 14, 8, 0), same);

        LocalDateTime next = service.nextRunAt(sub, LocalDateTime.of(2026, 10, 14, 9, 0));
        assertEquals(LocalDateTime.of(2026, 10, 21, 8, 0), next);
        assertEquals(DayOfWeek.WEDNESDAY, next.getDayOfWeek());
    }

    @Test
    @DisplayName("AC-B4-1 按月：命中本月几号，已过则顺延一月")
    void nextRunAtMonthly() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "MONTH", null, 5);

        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 0),
                service.nextRunAt(sub, LocalDateTime.of(2026, 10, 3, 9, 0)));
        assertEquals(LocalDateTime.of(2026, 11, 5, 8, 0),
                service.nextRunAt(sub, LocalDateTime.of(2026, 10, 5, 9, 0)));
    }

    @Test
    @DisplayName("AC-B4-1 按月限定 1–28：2 月也一定有这一天，不存在取不到的日期")
    void nextRunAtMonthlyWorksInFebruary() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "MONTH", null, 28);
        LocalDateTime at = service.nextRunAt(sub, LocalDateTime.of(2027, 2, 1, 0, 0));
        assertEquals(LocalDateTime.of(2027, 2, 28, 8, 0), at);
    }

    // ===================== 报告周期（AC-B4-2/B4-4） =====================

    @Test
    @DisplayName("AC-B4-2 报告覆盖「上一个已结束的自然周期」，不采用滚动窗口")
    void periodOfCoversPreviousNaturalPeriod() {
        PlmSubscription daily = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime[] day = service.periodOf(daily, LocalDateTime.of(2026, 10, 14, 8, 0));
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 0), day[0]);
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), day[1]);

        PlmSubscription weekly = SubscriptionTestSupport.periodSub(2L, "WEEK", 3, null);
        LocalDateTime[] week = service.periodOf(weekly, LocalDateTime.of(2026, 10, 14, 8, 0));
        assertEquals(DayOfWeek.MONDAY, week[0].getDayOfWeek());
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), week[0]);
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0), week[1]);

        PlmSubscription monthly = SubscriptionTestSupport.periodSub(3L, "MONTH", null, 5);
        LocalDateTime[] month = service.periodOf(monthly, LocalDateTime.of(2026, 10, 14, 8, 0));
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), month[0]);
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), month[1]);
    }

    @Test
    @DisplayName("AC-B4-4 区间左闭右开：相邻两期首尾相接且不重叠")
    void periodIsHalfOpen() {
        PlmSubscription daily = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        LocalDateTime[] d13 = service.periodOf(daily, LocalDateTime.of(2026, 10, 14, 8, 0));
        LocalDateTime[] d14 = service.periodOf(daily, LocalDateTime.of(2026, 10, 15, 8, 0));
        assertEquals(d13[1], d14[0], "前一期终点即后一期起点");
        assertTrue(d13[0].isBefore(d13[1]));
    }

    // ===================== 单条订阅（AC-B3） =====================

    @Test
    @DisplayName("AC-B3-1 单条订阅落库指向该资料，编号取自序列")
    void subscribeItemBindsLedger() {
        PlmLedger ledger = new PlmLedger();
        ledger.setId(100L);
        ledger.setLedgerNo("EV-20261014-0001");
        ledger.setName("投光灯检验报告");
        when(ledgerMapper.selectById(100L)).thenReturn(ledger);
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("ITEM");
        req.setLedgerId(100L);
        SubscriptionVO vo = service.create(req);

        assertEquals("SUB-20261014-0001", vo.getSubNo());
        assertEquals(100L, vo.getLedgerId());
        assertEquals("EV-20261014-0001", vo.getLedgerNo());
        assertEquals("资料变更：EV-20261014-0001", vo.getName(), "未命名时取证据编号");
        verify(ledgerService).requireReadable(100L);
    }

    @Test
    @DisplayName("AC-B3-6 同一用户重复订阅同一资料被拒（不同用户互不影响靠 user_id 隔离）")
    void duplicateItemSubscriptionRejected() {
        when(subscriptionMapper.selectCount(any())).thenReturn(1L);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("ITEM");
        req.setLedgerId(100L);
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("已订阅"));
        verify(subscriptionMapper, never()).insert(any(PlmSubscription.class));
    }

    @Test
    @DisplayName("AC-B3-8 无查看权限不能订阅：权限判定直接复用台账的 requireReadable")
    void subscribeWithoutReadPermissionRejected() {
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);
        doThrow(new BusinessException(com.hjgd.plm.common.ResultCode.FORBIDDEN, "无权限"))
                .when(ledgerService).requireReadable(100L);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("ITEM");
        req.setLedgerId(100L);
        assertThrows(BusinessException.class, () -> service.create(req));
        verify(subscriptionMapper, never()).insert(any(PlmSubscription.class));
    }

    @Test
    @DisplayName("AC-B3-5 退订只删该用户的订阅；未订阅时静默返回")
    void unsubscribeItemOnlyTouchesMySubscription() {
        PlmSubscription mine = new PlmSubscription();
        mine.setId(5L);
        when(subscriptionMapper.selectOne(any())).thenReturn(mine);

        service.unsubscribeItem(100L);
        verify(subscriptionMapper).deleteById(5L);

        when(subscriptionMapper.selectOne(any())).thenReturn(null);
        assertDoesNotThrow(() -> service.unsubscribeItem(100L));
    }

    @Test
    @DisplayName("AC-B3-7 资料作废/删除后单条订阅自动失效")
    void invalidateItemSubscriptions() {
        PlmSubscription a = new PlmSubscription();
        a.setId(1L);
        PlmSubscription b = new PlmSubscription();
        b.setId(2L);
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(a, b));

        service.invalidateItemSubscriptions(100L);

        verify(subscriptionMapper).deleteById(1L);
        verify(subscriptionMapper).deleteById(2L);
    }

    // ===================== 周期订阅（AC-B4） =====================

    @Test
    @DisplayName("AC-B4-9 周期订阅上限 20 条/人")
    void periodSubscriptionLimitPerUser() {
        properties.setMaxPeriodPerUser(20);
        when(subscriptionMapper.selectCount(any())).thenReturn(20L);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("PERIOD");
        req.setPeriod("DAY");
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("20"));
        verify(subscriptionMapper, never()).insert(any(PlmSubscription.class));
    }

    @Test
    @DisplayName("AC-B4-1 按周必须给星期几、按月必须给 1–28 的日期")
    void periodGranularityRequiresItsOwnField() {
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);

        SubscriptionRequest weekly = new SubscriptionRequest();
        weekly.setType("PERIOD");
        weekly.setPeriod("WEEK");
        assertThrows(BusinessException.class, () -> service.create(weekly));

        SubscriptionRequest monthly = new SubscriptionRequest();
        monthly.setType("PERIOD");
        monthly.setPeriod("MONTH");
        monthly.setMonthday(29);
        assertThrows(BusinessException.class, () -> service.create(monthly));
    }

    @Test
    @DisplayName("AC-B4-5 未命名时按「范围 + 周期 + 时点」自动生成订阅名")
    void periodNameAutoGenerated() {
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("PERIOD");
        req.setPeriod("WEEK");
        req.setWeekday(1);
        req.setPushTime("09:30");
        SubscriptionVO vo = service.create(req);

        assertEquals("本部门全部资料（每周一 09:30）", vo.getName());
    }

    @Test
    @DisplayName("AC-B4-3 分类条件越界（不在可见范围）直接拒绝")
    void periodFilterOutOfScopeRejected() {
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);
        doThrow(new BusinessException(com.hjgd.plm.common.ResultCode.PARAM_ERROR, "分类维度不可见: 5"))
                .when(categoryService).validateFilter(any());

        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("PERIOD");
        req.setPeriod("DAY");
        CategoryFilter filter = new CategoryFilter();
        CategoryFilterGroup group = new CategoryFilterGroup();
        group.setDimId(5L);
        group.setValueIds(List.of(12L));
        filter.setDims(List.of(group));
        req.setFilter(filter);

        assertThrows(BusinessException.class, () -> service.create(req));
        verify(subscriptionMapper, never()).insert(any(PlmSubscription.class));
    }

    @Test
    @DisplayName("AC-B4-7 改周期/时点后游标前移到下一完整周期，不补发历史")
    void updatePeriodResetsCursor() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        sub.setNextRunAt(LocalDateTime.now().minusDays(3));
        when(subscriptionMapper.selectById(1L)).thenReturn(sub);

        SubscriptionRequest req = new SubscriptionRequest();
        req.setPeriod("WEEK");
        req.setWeekday(1);
        req.setPushTime("10:00");
        SubscriptionVO vo = service.update(1L, req);

        assertEquals("WEEK", vo.getPeriod());
        assertNotNull(vo.getNextRunAt());
        assertTrue(vo.getNextRunAt().isAfter(LocalDateTime.now().minusMinutes(1)),
                "游标应前移到未来，历史期不补发");
        verify(subscriptionMapper).updateById(sub);
    }

    @Test
    @DisplayName("AC-B4-6 恢复订阅后从下一个完整周期重新起算")
    void resumeRecomputesCursor() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        sub.setStatus("PAUSED");
        sub.setNextRunAt(LocalDateTime.now().minusDays(10));
        when(subscriptionMapper.selectById(1L)).thenReturn(sub);

        SubscriptionVO vo = service.setStatus(1L, "enabled");

        assertEquals("ENABLED", vo.getStatus());
        assertTrue(vo.getNextRunAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    @DisplayName("AC-B4-8 删除订阅不删历史报告（只删订阅行）")
    void deleteKeepsReports() {
        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        when(subscriptionMapper.selectById(1L)).thenReturn(sub);

        service.delete(1L);

        verify(subscriptionMapper).deleteById(1L);
    }

    @Test
    @DisplayName("非本人订阅不可见：读改删一律「无权限」")
    void foreignSubscriptionIsForbidden() {
        PlmSubscription other = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        other.setUserId(999L);
        when(subscriptionMapper.selectById(1L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.delete(1L));
        assertThrows(BusinessException.class, () -> service.setStatus(1L, SubscriptionService.STATUS_PAUSED));
        verify(subscriptionMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("非法订阅类型/状态被拒")
    void invalidTypeAndStatusRejected() {
        SubscriptionRequest bad = new SubscriptionRequest();
        bad.setType("EVERYTHING");
        assertThrows(BusinessException.class, () -> service.create(bad));

        PlmSubscription sub = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        when(subscriptionMapper.selectById(1L)).thenReturn(sub);
        assertThrows(BusinessException.class, () -> service.setStatus(1L, "DELETED"));
    }

    @Test
    @DisplayName("AC-B5-1 到期待生成的订阅只取 ENABLED 且 next_run_at <= now")
    void listDueOnlyEnabledPeriodSubscriptions() {
        PlmSubscription due = SubscriptionTestSupport.periodSub(1L, "DAY", null, null);
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(due));

        List<PlmSubscription> list = service.listDue(LocalDateTime.of(2026, 10, 14, 8, 0), 200);

        assertEquals(1, list.size());
        verify(subscriptionMapper).selectList(any());
    }

    @Test
    @DisplayName("推送时点格式非法被拒（HH:mm）")
    void invalidPushTimeRejected() {
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);
        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("PERIOD");
        req.setPeriod("DAY");
        req.setPushTime("25:00");
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("HH:mm"));
    }

    @Test
    @DisplayName("订阅名称超长被拒")
    void nameTooLongRejected() {
        properties.setSubNameMax(4);
        when(subscriptionMapper.selectCount(any())).thenReturn(0L);
        SubscriptionRequest req = new SubscriptionRequest();
        req.setType("PERIOD");
        req.setPeriod("DAY");
        req.setName("超过四个字的订阅名称");
        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    @DisplayName("AC-B3-1 详情页订阅状态回显")
    void itemStateReflectsSubscription() {
        PlmSubscription sub = new PlmSubscription();
        sub.setId(3L);
        sub.setSubNo("SUB-20261014-0003");
        when(subscriptionMapper.selectOne(any())).thenReturn(sub);

        assertEquals(true, service.itemState(100L).get("subscribed"));
        assertEquals("SUB-20261014-0003", service.itemState(100L).get("subNo"));

        when(subscriptionMapper.selectOne(any())).thenReturn(null);
        assertEquals(false, service.itemState(100L).get("subscribed"));
    }

    @Test
    @DisplayName("默认推送时点取配置值 08:00")
    void defaultPushTimeFromProperties() {
        assertEquals(LocalTime.of(8, 0), LocalTime.parse(properties.getDefaultPushTime()));
    }
}
