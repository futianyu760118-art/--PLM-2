package com.hjgd.plm.ledger.service.impl;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.service.LedgerSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 双向同步实现（AC-A3）。
 *
 * <p>重试用 {@link TransactionTemplate} 包裹单次尝试：每次失败都先回滚再重投，
 * 避免把半途写入的部分字段留在库中（AC-A3-8）。
 */
@Slf4j
@Service
public class LedgerSyncServiceImpl implements LedgerSyncService {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_BACKOFF_MS = 100L;

    /** 允许从模块侧回写的台账字段（白名单，防止越字段写入） */
    private static final Set<String> WRITABLE_FIELDS = Set.of(
            "name", "type", "ownerId", "occurDate", "version", "deptId", "status", "remark");

    private final PlmLedgerMapper ledgerMapper;
    private final LedgerAuditService auditService;
    private final LedgerNotifyService notifyService;
    private final LedgerProperties properties;
    private final TransactionTemplate txTemplate;

    public LedgerSyncServiceImpl(PlmLedgerMapper ledgerMapper,
                                 LedgerAuditService auditService,
                                 LedgerNotifyService notifyService,
                                 LedgerProperties properties,
                                 PlatformTransactionManager transactionManager) {
        this.ledgerMapper = ledgerMapper;
        this.auditService = auditService;
        this.notifyService = notifyService;
        this.properties = properties;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public SyncResult applyFromModule(String objectType, String objectId,
                                      Map<String, Object> changes, String sourceSystem) {
        if (!StringUtils.hasText(objectType) || !StringUtils.hasText(objectId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "对象类型与对象编号均不能为空");
        }
        if (changes == null || changes.isEmpty()) {
            return SyncResult.none();
        }
        Map<String, Object> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            if (WRITABLE_FIELDS.contains(e.getKey())) {
                accepted.put(e.getKey(), e.getValue());
            }
        }
        if (accepted.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "没有可同步的台账字段");
        }

        String type = objectType.trim().toUpperCase(java.util.Locale.ROOT);
        String id = objectId.trim();
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return txTemplate.execute(status -> doApply(type, id, accepted, sourceSystem));
            } catch (Exception e) {
                last = e;
                log.warn("台账同步失败(第 {}/{} 次) type={} id={}: {}",
                        attempt, MAX_RETRY, type, id, e.getMessage());
                if (attempt < MAX_RETRY) {
                    sleep(RETRY_BACKOFF_MS * attempt);
                }
            }
        }
        String msg = last == null ? "未知原因" : last.getMessage();
        auditService.logFailure("SYNC_FAILED", id, "同步重试 " + MAX_RETRY + " 次仍失败: " + msg);
        throw new BusinessException(ResultCode.FAILED, "证据信息同步失败: " + msg);
    }

    private SyncResult doApply(String objectType, String objectId,
                               Map<String, Object> changes, String sourceSystem) {
        List<PlmLedger> targets = ledgerMapper.selectByObject(objectType, objectId);
        if (targets.isEmpty()) {
            // 该对象未关联任何证据，模块侧无需同步
            return SyncResult.none();
        }
        String operator = operator();
        SyncResult result = SyncResult.none();
        for (PlmLedger ledger : targets) {
            Map<String, Object> before = snapshot(ledger);
            List<String> appliedFields = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                Object oldValue = read(ledger, e.getKey());
                Object newValue = coerce(e.getKey(), e.getValue());
                if (Objects.equals(normalize(oldValue), normalize(newValue))) {
                    continue;
                }
                write(ledger, e.getKey(), newValue);
                appliedFields.add(e.getKey());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("field", e.getKey());
                row.put("before", oldValue);
                row.put("after", newValue);
                row.put("operator", operator);
                row.put("time", LocalDateTime.now().toString());
                rows.add(row);
            }
            if (appliedFields.isEmpty()) {
                continue;
            }
            // 后写优先（LWW）：本次写入生效，之前写入方成为「被覆盖方」
            boolean conflict = overwritesOtherSide(ledger, operator);
            Long overwrittenUser = ledger.getUploadedBy();
            ledger.setLastModifiedBy(operator);
            ledger.setLastModifiedAt(LocalDateTime.now());
            ledger.setRevision((ledger.getRevision() == null ? 0 : ledger.getRevision()) + 1);
            ledgerMapper.updateById(ledger);

            Map<String, Object> after = snapshot(ledger);
            auditService.log("SYNC_FROM_" + (sourceSystem == null ? "MODULE" : sourceSystem),
                    ledger.getLedgerNo(), before, after);
            if (conflict) {
                // 通知被覆盖方：内容含对象/字段/前后值/操作人/时间/链接（AC-A3-7、AC-A4-7/8）
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("ledgerNo", ledger.getLedgerNo());
                extra.put("ledgerName", nz(ledger.getName()));
                extra.put("overwritten", true);
                extra.put("link", "/ledger/list?keyword=" + ledger.getLedgerNo());
                notifyService.notify(overwrittenUser, LedgerNotifyService.TYPE_LEDGER_CHANGED,
                        "证据 " + ledger.getLedgerNo() + " 的字段已被 " + sourceSystem + " 侧覆盖",
                        objectType, objectId, rows, extra);
            }
            result = new SyncResult(ledger.getId(), ledger.getLedgerNo(),
                    appliedFields.size(), appliedFields, conflict);
        }
        return result;
    }

    /**
     * 台账侧是否在聚合窗口内刚写过同一行。命中即视为「另一方刚写入的值被本次覆盖」，
     * 需要按 AC-A3-7 通知被覆盖方。
     */
    private boolean overwritesOtherSide(PlmLedger ledger, String operator) {
        if (ledger.getLastModifiedAt() == null) {
            return false;
        }
        if (Objects.equals(ledger.getLastModifiedBy(), operator)) {
            return false;
        }
        Duration age = Duration.between(ledger.getLastModifiedAt(), LocalDateTime.now());
        return age.toMinutes() < Math.max(1, properties.getNotifyAggregateMinutes());
    }

    private Object read(PlmLedger ledger, String field) {
        return switch (field) {
            case "name" -> ledger.getName();
            case "type" -> ledger.getType();
            case "ownerId" -> ledger.getOwnerId();
            case "occurDate" -> ledger.getOccurDate() == null ? null : ledger.getOccurDate().toString();
            case "version" -> ledger.getVersion();
            case "deptId" -> ledger.getDeptId();
            case "status" -> ledger.getStatus();
            case "remark" -> ledger.getRemark();
            default -> null;
        };
    }

    private void write(PlmLedger ledger, String field, Object value) {
        switch (field) {
            case "name" -> ledger.setName(value == null ? null : String.valueOf(value));
            case "type" -> ledger.setType(value == null ? null : String.valueOf(value));
            case "ownerId" -> ledger.setOwnerId(toLong(value));
            case "occurDate" -> ledger.setOccurDate(toDate(value));
            case "version" -> ledger.setVersion(value == null ? null : String.valueOf(value));
            case "deptId" -> ledger.setDeptId(toLong(value));
            case "status" -> ledger.setStatus(value == null ? null : String.valueOf(value));
            case "remark" -> ledger.setRemark(value == null ? null : String.valueOf(value));
            default -> { }
        }
    }

    private Object coerce(String field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case "ownerId", "deptId" -> toLong(value);
            case "occurDate" -> {
                LocalDate d = toDate(value);
                yield d == null ? null : d.toString();
            }
            default -> String.valueOf(value);
        };
    }

    private Long toLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段应为数字: " + value);
        }
    }

    private LocalDate toDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String v = String.valueOf(value).trim();
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd: " + value);
        }
    }

    private Map<String, Object> snapshot(PlmLedger ledger) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", ledger.getName());
        m.put("type", ledger.getType());
        m.put("ownerId", ledger.getOwnerId());
        m.put("occurDate", ledger.getOccurDate() == null ? null : ledger.getOccurDate().toString());
        m.put("version", ledger.getVersion());
        m.put("deptId", ledger.getDeptId());
        m.put("status", ledger.getStatus());
        m.put("remark", ledger.getRemark());
        return m;
    }

    private Object normalize(Object o) {
        return o == null ? "" : o;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String operator() {
        try {
            return SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return "MODULE_SYNC";
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
