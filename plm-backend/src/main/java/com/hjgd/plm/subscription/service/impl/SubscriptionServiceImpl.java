package com.hjgd.plm.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ledger.dto.CategoryFilter;
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
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 订阅实现（B3 单条 / B4 周期）。
 *
 * <p>「下一生成时点」是纯计算，不放定时器也不放队列：订阅的粒度最细到自然日，
 * 由调度任务每分钟扫一次 {@code next_run_at} 到期即生成（AC-B5-1 偏差 ≤10 分钟）。
 * 好处是改周期/暂停/恢复只需要重算一个时间戳，不涉及已有任务的迁移（AC-B4-6/7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final String SEQ_SUB = "SUBSCRIPTION_NO";
    private static final Set<String> PERIODS = Set.of("DAY", "WEEK", "MONTH");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final PlmSubscriptionMapper subscriptionMapper;
    private final PlmLedgerMapper ledgerMapper;
    private final LedgerService ledgerService;
    private final CategoryService categoryService;
    private final SubscriptionProperties properties;
    private final SequenceService sequenceService;
    private final ObjectMapper objectMapper;

    // ===================== 我的订阅 =====================

    @Override
    public PageResult<SubscriptionVO> page(Integer pageNum, Integer pageSize, String type, String status) {
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        LambdaQueryWrapper<PlmSubscription> w = Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getUserId, currentUserId());
        if (StringUtils.hasText(type)) {
            w.eq(PlmSubscription::getType, type.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            w.eq(PlmSubscription::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        w.orderByDesc(PlmSubscription::getId);
        IPage<PlmSubscription> page = subscriptionMapper.selectPage(new Page<>(num, size), w);
        List<SubscriptionVO> rows = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), rows);
    }

    @Override
    @Transactional
    public SubscriptionVO create(SubscriptionRequest request) {
        Long userId = currentUserId();
        String type = requireType(request.getType());
        PlmSubscription sub = new PlmSubscription();
        sub.setSubNo(sequenceService.nextNo(SEQ_SUB));
        sub.setUserId(userId);
        sub.setType(type);
        sub.setStatus(STATUS_ENABLED);

        if (TYPE_ITEM.equals(type)) {
            Long ledgerId = request.getLedgerId();
            if (ledgerId == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "单条订阅必须指定资料");
            }
            // AC-B3-8：无查看权限不能订阅（入口不可见或点击后提示无权限）
            ledgerService.requireReadable(ledgerId);
            Long dup = subscriptionMapper.selectCount(Wrappers.<PlmSubscription>lambdaQuery()
                    .eq(PlmSubscription::getUserId, userId)
                    .eq(PlmSubscription::getType, TYPE_ITEM)
                    .eq(PlmSubscription::getLedgerId, ledgerId));
            if (dup != null && dup > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "已订阅该条资料");
            }
            PlmLedger ledger = ledgerMapper.selectById(ledgerId);
            sub.setLedgerId(ledgerId);
            sub.setName(StringUtils.hasText(request.getName())
                    ? trimName(request.getName())
                    : "资料变更：" + ledger.getLedgerNo());
            sub.setPushTime(LocalTime.parse(properties.getDefaultPushTime(), TIME_FMT));
            subscriptionMapper.insert(sub);
            return toVO(sub);
        }

        // ---------- 周期订阅 ----------
        long periodCount = subscriptionMapper.selectCount(Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getUserId, userId)
                .eq(PlmSubscription::getType, TYPE_PERIOD));
        if (periodCount >= properties.getMaxPeriodPerUser()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "周期订阅数量已达上限 " + properties.getMaxPeriodPerUser() + " 条");
        }
        applyPeriodFields(sub, request, true);
        subscriptionMapper.insert(sub);
        return toVO(sub);
    }

    @Override
    @Transactional
    public SubscriptionVO update(Long id, SubscriptionRequest request) {
        PlmSubscription sub = requireMine(id);
        if (TYPE_ITEM.equals(sub.getType())) {
            if (StringUtils.hasText(request.getName())) {
                sub.setName(trimName(request.getName()));
            }
            subscriptionMapper.updateById(sub);
            return toVO(sub);
        }
        boolean periodChanged = applyPeriodFields(sub, request, false);
        // 改周期/时点后从下一个完整周期起生效：重算游标，不补发历史（AC-B4-7）
        if (periodChanged) {
            sub.setNextRunAt(nextRunAt(sub, LocalDateTime.now()));
        }
        subscriptionMapper.updateById(sub);
        return toVO(sub);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PlmSubscription sub = requireMine(id);
        // 已生成的历史报告独立留存，删订阅不删报告（AC-B4-8）
        subscriptionMapper.deleteById(sub.getId());
    }

    @Override
    @Transactional
    public SubscriptionVO setStatus(Long id, String status) {
        PlmSubscription sub = requireMine(id);
        String target = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
        if (!STATUS_ENABLED.equals(target) && !STATUS_PAUSED.equals(target)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法订阅状态: " + status);
        }
        sub.setStatus(target);
        if (STATUS_ENABLED.equals(target) && TYPE_PERIOD.equals(sub.getType())) {
            // 恢复后从恢复时刻所属的下一个完整周期开始，暂停期间不补发（AC-B4-6）
            sub.setNextRunAt(nextRunAt(sub, LocalDateTime.now()));
        }
        subscriptionMapper.updateById(sub);
        return toVO(sub);
    }

    // ===================== 单条订阅 =====================

    @Override
    public Map<String, Object> itemState(Long ledgerId) {
        Long userId = currentUserId();
        PlmSubscription sub = subscriptionMapper.selectOne(Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getUserId, userId)
                .eq(PlmSubscription::getType, TYPE_ITEM)
                .eq(PlmSubscription::getLedgerId, ledgerId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subscribed", sub != null);
        out.put("subscriptionId", sub == null ? null : sub.getId());
        out.put("subNo", sub == null ? null : sub.getSubNo());
        return out;
    }

    @Override
    @Transactional
    public SubscriptionVO subscribeItem(Long ledgerId) {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setType(TYPE_ITEM);
        request.setLedgerId(ledgerId);
        return create(request);
    }

    @Override
    @Transactional
    public void unsubscribeItem(Long ledgerId) {
        PlmSubscription sub = subscriptionMapper.selectOne(Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getUserId, currentUserId())
                .eq(PlmSubscription::getType, TYPE_ITEM)
                .eq(PlmSubscription::getLedgerId, ledgerId));
        if (sub == null) {
            return;
        }
        subscriptionMapper.deleteById(sub.getId());
    }

    // ===================== 供报告与监听方 =====================

    @Override
    public List<PlmSubscription> subscribersOf(Long ledgerId) {
        return subscriptionMapper.selectList(Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getType, TYPE_ITEM)
                .eq(PlmSubscription::getLedgerId, ledgerId)
                .eq(PlmSubscription::getStatus, STATUS_ENABLED));
    }

    @Override
    @Transactional
    public void invalidateItemSubscriptions(Long ledgerId) {
        for (PlmSubscription sub : subscribersOf(ledgerId)) {
            subscriptionMapper.deleteById(sub.getId());
        }
    }

    @Override
    public List<PlmSubscription> listDue(LocalDateTime now, int limit) {
        return subscriptionMapper.selectList(Wrappers.<PlmSubscription>lambdaQuery()
                .eq(PlmSubscription::getType, TYPE_PERIOD)
                .eq(PlmSubscription::getStatus, STATUS_ENABLED)
                .isNotNull(PlmSubscription::getNextRunAt)
                .le(PlmSubscription::getNextRunAt, now)
                .orderByAsc(PlmSubscription::getNextRunAt)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public LocalDateTime nextRunAt(PlmSubscription subscription, LocalDateTime from) {
        LocalTime time = subscription.getPushTime() == null
                ? LocalTime.parse(properties.getDefaultPushTime(), TIME_FMT)
                : subscription.getPushTime();
        String period = subscription.getPeriod();
        if (period == null) {
            period = "DAY";
        }
        switch (period) {
            case "WEEK" -> {
                int weekday = subscription.getWeekday() == null ? 1 : subscription.getWeekday();
                LocalDateTime candidate = from.toLocalDate().atTime(time);
                int delta = (weekday - candidate.getDayOfWeek().getValue() + 7) % 7;
                candidate = candidate.plusDays(delta);
                if (!candidate.isAfter(from)) {
                    candidate = candidate.plusWeeks(1);
                }
                return candidate;
            }
            case "MONTH" -> {
                int monthday = subscription.getMonthday() == null ? 1 : subscription.getMonthday();
                LocalDateTime candidate = atMonthDay(from.toLocalDate(), monthday, time);
                if (!candidate.isAfter(from)) {
                    candidate = atMonthDay(from.toLocalDate().plusMonths(1), monthday, time);
                }
                return candidate;
            }
            default -> {
                LocalDateTime candidate = from.toLocalDate().atTime(time);
                return candidate.isAfter(from) ? candidate : candidate.plusDays(1);
            }
        }
    }

    @Override
    public LocalDateTime[] periodOf(PlmSubscription subscription, LocalDateTime runAt) {
        LocalDate date = runAt.toLocalDate();
        String period = subscription.getPeriod() == null ? "DAY" : subscription.getPeriod();
        return switch (period) {
            // 上一段已结束的自然周（周一 00:00 起算，AC-B4-2 不采用滚动窗口）
            case "WEEK" -> {
                LocalDate thisMonday = date.with(DayOfWeek.MONDAY);
                yield new LocalDateTime[]{thisMonday.minusWeeks(1).atStartOfDay(), thisMonday.atStartOfDay()};
            }
            case "MONTH" -> {
                LocalDate thisMonth = date.withDayOfMonth(1);
                yield new LocalDateTime[]{thisMonth.minusMonths(1).atStartOfDay(), thisMonth.atStartOfDay()};
            }
            default -> new LocalDateTime[]{date.minusDays(1).atStartOfDay(), date.atStartOfDay()};
        };
    }

    // ===================== 内部 =====================

    /**
     * 写入周期相关字段。返回「周期/时点是否变化」——变化决定要不要把游标前移到下一完整周期（AC-B4-7）。
     */
    private boolean applyPeriodFields(PlmSubscription sub, SubscriptionRequest request, boolean creating) {
        String period = StringUtils.hasText(request.getPeriod())
                ? request.getPeriod().trim().toUpperCase(Locale.ROOT) : sub.getPeriod();
        if (creating && !StringUtils.hasText(period)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "周期订阅必须指定周期粒度");
        }
        if (StringUtils.hasText(period) && !PERIODS.contains(period)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "周期粒度须为 按日/按周/按月");
        }
        Integer weekday = request.getWeekday() == null ? sub.getWeekday() : request.getWeekday();
        Integer monthday = request.getMonthday() == null ? sub.getMonthday() : request.getMonthday();
        LocalTime pushTime = StringUtils.hasText(request.getPushTime())
                ? parseTime(request.getPushTime()) : sub.getPushTime();
        if (pushTime == null) {
            pushTime = LocalTime.parse(properties.getDefaultPushTime(), TIME_FMT);
        }
        if ("WEEK".equals(period) && (weekday == null || weekday < 1 || weekday > 7)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "按周订阅需指定星期几（1=周一 … 7=周日）");
        }
        if ("MONTH".equals(period) && (monthday == null || monthday < 1 || monthday > 28)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "按月订阅需指定每月几号（1–28）");
        }
        CategoryFilter filter = request.getFilter();
        if (filter != null) {
            // 分类条件必须落在当前用户可见范围内（AC-B2-4）
            categoryService.validateFilter(filter);
        } else if (creating) {
            filter = new CategoryFilter();
        }

        boolean changed = creating
                || !equalsSafe(period, sub.getPeriod())
                || !equalsSafe(weekday, sub.getWeekday())
                || !equalsSafe(monthday, sub.getMonthday())
                || !equalsSafe(pushTime, sub.getPushTime())
                || filter != null;

        sub.setPeriod(period);
        sub.setWeekday(weekday);
        sub.setMonthday(monthday);
        sub.setPushTime(pushTime);
        if (filter != null) {
            sub.setFilterJson(writeFilter(filter));
        }
        if (creating || StringUtils.hasText(request.getName())) {
            sub.setName(StringUtils.hasText(request.getName())
                    ? trimName(request.getName())
                    : defaultPeriodName(filter, period, weekday, monthday, pushTime));
        }
        if (creating) {
            sub.setNextRunAt(nextRunAt(sub, LocalDateTime.now()));
        }
        return changed;
    }

    private String defaultPeriodName(CategoryFilter filter, String period, Integer weekday,
                                     Integer monthday, LocalTime pushTime) {
        String scope = categoryService.describeFilter(filter);
        String when = switch (period == null ? "DAY" : period) {
            case "WEEK" -> "每周" + weekdayName(weekday) + " ";
            case "MONTH" -> "每月" + (monthday == null ? 1 : monthday) + "日 ";
            default -> "每日 ";
        };
        return scope + "（" + when + pushTime.format(TIME_FMT) + "）";
    }

    private String weekdayName(Integer weekday) {
        return switch (weekday == null ? 1 : weekday) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> "日";
        };
    }

    private LocalDateTime atMonthDay(LocalDate month, int monthday, LocalTime time) {
        int day = Math.min(monthday, month.lengthOfMonth());
        return month.withDayOfMonth(day).atTime(time);
    }

    private LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw.trim(), TIME_FMT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "推送时点格式应为 HH:mm");
        }
    }

    private String requireType(String raw) {
        String type = StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : "";
        if (!TYPE_ITEM.equals(type) && !TYPE_PERIOD.equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订阅类型须为 单条 或 周期");
        }
        return type;
    }

    private PlmSubscription requireMine(Long id) {
        PlmSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订阅不存在");
        }
        if (!sub.getUserId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
        return sub;
    }

    private String trimName(String raw) {
        String name = raw.trim();
        if (name.length() > properties.getSubNameMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "订阅名称不能超过 " + properties.getSubNameMax() + " 字");
        }
        return name;
    }

    private SubscriptionVO toVO(PlmSubscription sub) {
        SubscriptionVO vo = new SubscriptionVO();
        vo.setId(sub.getId());
        vo.setSubNo(sub.getSubNo());
        vo.setType(sub.getType());
        vo.setName(sub.getName());
        vo.setLedgerId(sub.getLedgerId());
        vo.setPeriod(sub.getPeriod());
        vo.setWeekday(sub.getWeekday());
        vo.setMonthday(sub.getMonthday());
        vo.setPushTime(sub.getPushTime() == null ? null : sub.getPushTime().format(TIME_FMT));
        vo.setStatus(sub.getStatus());
        vo.setNextRunAt(sub.getNextRunAt());
        vo.setCreatedAt(sub.getCreatedAt());
        if (TYPE_ITEM.equals(sub.getType()) && sub.getLedgerId() != null) {
            PlmLedger ledger = ledgerMapper.selectById(sub.getLedgerId());
            if (ledger != null) {
                vo.setLedgerNo(ledger.getLedgerNo());
                vo.setLedgerName(ledger.getName());
            }
        }
        if (TYPE_PERIOD.equals(sub.getType())) {
            CategoryFilter filter = readFilter(sub.getFilterJson());
            vo.setFilter(filter);
            vo.setFilterText(categoryService.describeFilter(filter));
        }
        return vo;
    }

    private CategoryFilter readFilter(String json) {
        if (!StringUtils.hasText(json)) {
            return new CategoryFilter();
        }
        try {
            return objectMapper.readValue(json, CategoryFilter.class);
        } catch (Exception e) {
            log.warn("订阅分类条件解析失败，按不筛选处理: {}", e.getMessage());
            return new CategoryFilter();
        }
    }

    private String writeFilter(CategoryFilter filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类条件格式不正确");
        }
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    private boolean equalsSafe(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
