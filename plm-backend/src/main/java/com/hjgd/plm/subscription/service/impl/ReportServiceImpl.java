package com.hjgd.plm.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ledger.dto.CategoryFilter;
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
import com.hjgd.plm.subscription.service.ReportService;
import com.hjgd.plm.subscription.service.SubscriptionService;
import com.hjgd.plm.subscription.spi.ReportChannelSender;
import com.hjgd.plm.subscription.support.ReportExcelWriter;
import com.hjgd.plm.subscription.support.ReportHtmlBuilder;
import com.hjgd.plm.subscription.support.UserScopeResolver;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 报告实现（B5 + B3 报告留档）。
 *
 * <p>刻意不加 {@code @Transactional}：报表流程里最需要落库的一条恰恰是<b>失败记录</b>
 * （AC-B5-5），而失败往往来自底层取数异常；把生成与留痕放进同一个事务，
 * 异常一传播整笔回滚，「失败报告」就永远写不进去。单份报告没有跨多表的原子性诉求，
 * 逐条提交反而让每一步都可以独立留痕。
 *
 * <p>Excel 明细在需要时由 {@link ReportExcelWriter} 从报告自身的 contentJson 现生成，
 * 不落文件：既保证「下载到的」与「当时推送的」必然一致，也不留无人清理的历史文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String SEQ_REPORT = "REPORT_NO";
    private static final String STATUS_GENERATED = "GENERATED";
    private static final String STATUS_PUSHED = "PUSHED";
    private static final String STATUS_FAILED = "FAILED";

    private final PlmReportMapper reportMapper;
    private final PlmReportDeliveryLogMapper deliveryLogMapper;
    private final ReportItemMapper reportItemMapper;
    private final RepositoryItemAssembler assembler;
    private final UserScopeResolver scopeResolver;
    private final ReportHtmlBuilder htmlBuilder;
    private final ReportExcelWriter excelWriter;
    private final SubscriptionService subscriptionService;
    private final SubscriptionProperties properties;
    private final SequenceService sequenceService;
    private final LedgerNotifyService notifyService;
    private final PlmLedgerMapper ledgerMapper;
    private final ObjectMapper objectMapper;
    /** 投递渠道集合：站内通知 + 邮件；新增渠道只需再实现一个 bean，本类不改 */
    private final List<ReportChannelSender> senders;

    // ===================== 查询 =====================

    @Override
    public PageResult<ReportVO> page(Integer pageNum, Integer pageSize, String type) {
        int retain = Math.max(1, properties.getReportRetain());
        // AC-B5-7：只回溯最近 12 期。历史行不删除（审计要留），但不从列表暴露，
        // 因此这里把 pageSize 直接压到保留期数，避免翻页翻出「承诺之外」的报告。
        int size = pageSize == null || pageSize < 1 ? retain : Math.min(pageSize, retain);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        LambdaQueryWrapper<PlmReport> w = Wrappers.<PlmReport>lambdaQuery()
                .eq(PlmReport::getUserId, SecurityUtils.getCurrentUserId());
        if (StringUtils.hasText(type)) {
            w.eq(PlmReport::getType, type.trim().toUpperCase(Locale.ROOT));
        }
        w.orderByDesc(PlmReport::getId);
        IPage<PlmReport> page = reportMapper.selectPage(new Page<>(num, size), w);
        List<ReportVO> rows = new ArrayList<>();
        for (PlmReport report : page.getRecords()) {
            rows.add(toVO(report));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), rows);
    }

    @Override
    public ReportDetailVO detail(Long id) {
        PlmReport report = requireMine(id);
        ReportDetailVO vo = new ReportDetailVO();
        vo.setReport(toVO(report));
        vo.setItems(readItems(report.getContentJson()));
        List<PlmReportDeliveryLog> logs = deliveryLogMapper.selectList(
                Wrappers.<PlmReportDeliveryLog>lambdaQuery()
                        .eq(PlmReportDeliveryLog::getReportId, id)
                        .orderByAsc(PlmReportDeliveryLog::getId));
        List<ReportDetailVO.DeliveryLogVO> deliveries = new ArrayList<>();
        for (PlmReportDeliveryLog row : logs) {
            ReportDetailVO.DeliveryLogVO d = new ReportDetailVO.DeliveryLogVO();
            d.setChannel(row.getChannel());
            d.setResult(row.getResult());
            d.setTarget(row.getTarget());
            d.setError(row.getError());
            d.setDeliveredAt(row.getDeliveredAt());
            deliveries.add(d);
        }
        vo.setDeliveries(deliveries);
        return vo;
    }

    @Override
    public byte[] excelOf(Long id) {
        PlmReport report = requireMine(id);
        return excelWriter.write(report, readItems(report.getContentJson()));
    }

    @Override
    public String htmlOf(Long id) {
        return requireMine(id).getHtmlBody();
    }

    // ===================== 生成 =====================

    @Override
    public ReportVO generatePeriod(PlmSubscription subscription, LocalDateTime runAt) {
        UserScopeResolver.Scope scope = scopeResolver.resolve(subscription.getUserId());
        LocalDateTime[] period = subscriptionService.periodOf(subscription, runAt);
        CategoryFilter filter = readFilter(subscription.getFilterJson());

        List<RepositoryItemVO> items = null;
        String lastError = null;
        int attempts = 0;
        int maxAttempts = Math.max(1, properties.getReportRetry());
        for (int i = 1; i <= maxAttempts; i++) {
            attempts = i;
            try {
                // 报告取数与资料汇总处同一口径：订阅人看不到的资料，不会因为「进了报告」而泄露（AC-B6-9）
                List<PlmLedger> ledgers = reportItemMapper.selectPeriodItems(period[0], period[1], filter,
                        !scope.unrestricted(), scope.deptId(), scope.userId());
                items = assembler.assemble(ledgers, true);
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                log.warn("报告生成第 {}/{} 次尝试失败 subNo={} 区间=[{}, {}): {}",
                        i, maxAttempts, subscription.getSubNo(), period[0], period[1], lastError);
            }
        }
        if (items == null) {
            return recordFailure(subscription, attempts, lastError);
        }

        String html = htmlBuilder.buildPeriod(subscription.getName(), period[0], period[1], items);
        PlmReport report = insertReport(subscription, SubscriptionService.TYPE_PERIOD, period[0], period[1], items, html);
        report.setRetryCount(attempts - 1);
        push(report, items, true);
        return toVO(reportMapper.selectById(report.getId()));
    }

    @Override
    public ReportVO generateItem(PlmSubscription subscription, LedgerChangeEvent event) {
        RepositoryItemVO item = itemOf(event);
        List<RepositoryItemVO> items = List.of(item);
        LocalDateTime at = event.getOccurredAt() == null ? LocalDateTime.now() : event.getOccurredAt();
        String summary = StringUtils.hasText(event.getSummary()) ? event.getSummary() : "资料发生变更";
        String html = htmlBuilder.buildItem(subscription.getName(), item, summary, event.getChanges());
        // 单条报告的「周期区间」就是这次变更发生的那一刻
        PlmReport report = insertReport(subscription, SubscriptionService.TYPE_ITEM, at, at, items, html);
        push(report, items, false);
        return toVO(reportMapper.selectById(report.getId()));
    }

    @Override
    public boolean itemReportExistsSince(Long subscriptionId, LocalDateTime since) {
        if (subscriptionId == null || since == null) {
            return false;
        }
        Long n = reportMapper.selectCount(Wrappers.<PlmReport>lambdaQuery()
                .eq(PlmReport::getSubscriptionId, subscriptionId)
                .eq(PlmReport::getType, SubscriptionService.TYPE_ITEM)
                .ge(PlmReport::getGeneratedAt, since));
        return n != null && n > 0;
    }

    // ===================== 推送 =====================

    /**
     * 逐渠道投递并留痕（AC-B5-6）。
     *
     * @param withInbox 是否使用站内通知渠道：单条订阅传 false——那一条站内通知由变更推送
     *                  （{@code SUB_ITEM}）发出且带变更前后值，这里再发一条同事件的站内通知
     *                  会让用户以为资料被改了两次
     */
    private void push(PlmReport report, List<RepositoryItemVO> items, boolean withInbox) {
        byte[] excel = hasEmailChannel() ? excelWriter.write(report, items) : null;
        int success = 0;
        int failed = 0;
        for (ReportChannelSender sender : senders) {
            if (!withInbox && ReportChannelSender.CHANNEL_INBOX.equals(sender.channel())) {
                logDelivery(report, sender.channel(), ReportChannelSender.RESULT_SKIPPED, "站内通知",
                        "单条订阅的站内通知由变更推送承担，报告仅留档");
                continue;
            }
            ReportChannelSender.Delivery delivery;
            try {
                delivery = sender.send(report, items, excel);
            } catch (Exception e) {
                // 单个渠道炸掉不该影响其它渠道投递
                delivery = ReportChannelSender.Delivery.failed(sender.channel(), e.getMessage());
            }
            logDelivery(report, sender.channel(), delivery.result(), delivery.target(), delivery.error());
            if (ReportChannelSender.RESULT_SUCCESS.equals(delivery.result())) {
                success++;
            } else if (ReportChannelSender.RESULT_FAILED.equals(delivery.result())) {
                failed++;
            }
        }

        if (success == 0 && failed > 0) {
            // 渠道全挂：报告本身没问题，但订阅人收不到，按失败留痕并告知（AC-B5-5 的「通知订阅人」）
            report.setStatus(STATUS_FAILED);
            report.setError("全部推送渠道失败");
            reportMapper.updateById(report);
            notifyOwner(report, "报告推送失败", "报告 " + report.getReportNo() + " 已生成，但所有推送渠道均投递失败，请到「我的订阅-报告」查看。");
            return;
        }
        report.setStatus(STATUS_PUSHED);
        report.setPushedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private ReportVO recordFailure(PlmSubscription subscription, int attempts, String error) {
        PlmReport report = new PlmReport();
        report.setReportNo(sequenceService.nextNo(SEQ_REPORT));
        report.setSubscriptionId(subscription.getId());
        report.setUserId(subscription.getUserId());
        report.setType(SubscriptionService.TYPE_PERIOD);
        report.setSubName(subscription.getName());
        report.setItemCount(0);
        report.setContentJson("[]");
        report.setStatus(STATUS_FAILED);
        report.setRetryCount(attempts);
        report.setError(truncate(error));
        report.setGeneratedAt(LocalDateTime.now());
        reportMapper.insert(report);
        // AC-B5-5：重试仍失败要通知订阅人，且这次「已告知」同样落投递日志
        try {
            notifyOwner(report, "报告生成失败",
                    "订阅「" + nz(report.getSubName()) + "」的报告生成失败（已自动重试 " + attempts + " 次）："
                            + nz(error) + "。请联系系统管理员，或稍后在「我的订阅-报告」查看。");
            logDelivery(report, ReportChannelSender.CHANNEL_INBOX, ReportChannelSender.RESULT_SUCCESS,
                    "站内通知", null);
        } catch (Exception e) {
            logDelivery(report, ReportChannelSender.CHANNEL_INBOX, ReportChannelSender.RESULT_FAILED,
                    "站内通知", e.getMessage());
            log.warn("报告失败通知未送达 reportNo={}: {}", report.getReportNo(), e.getMessage());
        }
        return toVO(report);
    }

    private void notifyOwner(PlmReport report, String title, String summary) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reportId", report.getId());
        extra.put("reportNo", report.getReportNo());
        extra.put("subName", report.getSubName());
        extra.put("summary", summary);
        extra.put("reportLink", "/subscription/report?id=" + report.getId());
        notifyService.notify(report.getUserId(), LedgerNotifyService.TYPE_SUB_REPORT, title,
                "REPORT", String.valueOf(report.getId()), List.of(), extra);
    }

    private void logDelivery(PlmReport report, String channel, String result, String target, String error) {
        PlmReportDeliveryLog row = new PlmReportDeliveryLog();
        row.setReportId(report.getId());
        row.setChannel(channel);
        row.setResult(result);
        row.setTarget(target);
        row.setError(truncate(error));
        row.setDeliveredAt(LocalDateTime.now());
        deliveryLogMapper.insert(row);
    }

    // ===================== 内部 =====================

    private PlmReport insertReport(PlmSubscription subscription, String type, LocalDateTime start,
                                   LocalDateTime end, List<RepositoryItemVO> items, String html) {
        PlmReport report = new PlmReport();
        report.setReportNo(sequenceService.nextNo(SEQ_REPORT));
        report.setSubscriptionId(subscription.getId());
        report.setUserId(subscription.getUserId());
        report.setType(type);
        // 订阅名称取快照：订阅事后被改名或删除，历史报告仍能说明「这是哪条订阅的报告」（AC-B4-8）
        report.setSubName(subscription.getName());
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setItemCount(items.size());
        report.setContentJson(writeItems(items));
        report.setHtmlBody(html);
        report.setStatus(STATUS_GENERATED);
        report.setRetryCount(0);
        report.setGeneratedAt(LocalDateTime.now());
        reportMapper.insert(report);
        return report;
    }

    /**
     * 事件对应的资料条目。
     *
     * <p>删除/作废事件到达时台账行已被逻辑删除、查不到，此时仍要给出条目：
     * 报告是「当时发生了什么」的留痕，不该因为源行没了就只剩一句空话。
     */
    private RepositoryItemVO itemOf(LedgerChangeEvent event) {
        if (event.getLedgerId() != null) {
            PlmLedger ledger = ledgerMapper.selectById(event.getLedgerId());
            if (ledger != null) {
                List<RepositoryItemVO> one = assembler.assemble(List.of(ledger), true);
                if (!one.isEmpty()) {
                    return one.get(0);
                }
            }
        }
        RepositoryItemVO vo = new RepositoryItemVO();
        vo.setLedgerId(event.getLedgerId());
        vo.setLedgerNo(event.getLedgerNo());
        vo.setName(event.getLedgerName());
        vo.setType(event.getLedgerType());
        vo.setTypeName(event.getLedgerType());
        vo.setStatus(event.getStatus());
        vo.setOwnerId(event.getOwnerId());
        vo.setOwnerName("");
        vo.setDeptId(event.getDeptId());
        vo.setLink("/ledger/repository?ledgerId=" + event.getLedgerId());
        vo.setAttachmentCount(0);
        return vo;
    }

    private boolean hasEmailChannel() {
        for (ReportChannelSender sender : senders) {
            if (ReportChannelSender.CHANNEL_EMAIL.equals(sender.channel())) {
                return true;
            }
        }
        return false;
    }

    private PlmReport requireMine(Long id) {
        PlmReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        if (!report.getUserId().equals(SecurityUtils.getCurrentUserId())) {
            // AC-B5-3：链接指向的报告带权限校验，非订阅人拿不到内容
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
        return report;
    }

    private ReportVO toVO(PlmReport report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setReportNo(report.getReportNo());
        vo.setSubscriptionId(report.getSubscriptionId());
        vo.setSubName(report.getSubName());
        vo.setType(report.getType());
        vo.setPeriodStart(report.getPeriodStart());
        vo.setPeriodEnd(report.getPeriodEnd());
        vo.setItemCount(report.getItemCount());
        vo.setStatus(report.getStatus());
        vo.setRetryCount(report.getRetryCount());
        vo.setError(report.getError());
        vo.setGeneratedAt(report.getGeneratedAt());
        vo.setPushedAt(report.getPushedAt());
        // Excel 按需生成（内容来自 contentJson），所以只要有报告就有明细可下载
        vo.setHasExcel(StringUtils.hasText(report.getContentJson()));
        return vo;
    }

    private String writeItems(List<RepositoryItemVO> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAILED, "报告内容保存失败");
        }
    }

    private List<RepositoryItemVO> readItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RepositoryItemVO>>() {
            });
        } catch (Exception e) {
            // 快照读不出来不该让整份报告打不开：头信息仍在，明细降级为空并在日志里留因
            log.warn("报告明细解析失败，按空明细返回: {}", e.getMessage());
            return List.of();
        }
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

    private String truncate(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= 500 ? raw : raw.substring(0, 500);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
