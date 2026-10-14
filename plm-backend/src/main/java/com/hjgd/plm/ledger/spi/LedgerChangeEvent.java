package com.hjgd.plm.ledger.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 台账写操作事件（能力线 A → 能力线 B 的扩展点）。
 *
 * <p>台账侧不关心谁订阅了这个事件：A 只保证「每次落库成功后发一次」，
 * 单条订阅推送、报告增量等都由 B 侧监听实现消费（见 {@link LedgerChangeListener}）。
 *
 * <p>{@code changes} 沿用 A 的通知行结构（field / before / after / operator / time），
 * 与 {@code LedgerNotifyService} 同构，避免多一层翻译导致前后值失真。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerChangeEvent {

    public enum Kind {
        /** 新建台账 */
        CREATED,
        /** 台账字段变更（含状态改为作废/归档） */
        UPDATED,
        /** 台账删除 */
        DELETED,
        /** 附件追加 */
        ATTACH_ADDED,
        /** 附件移除 */
        ATTACH_REMOVED
    }

    private Kind kind;
    private Long ledgerId;
    private String ledgerNo;
    private String ledgerName;
    private String ledgerType;
    private String status;
    /** 责任人（台账 owner_id） */
    private Long ownerId;
    private Long deptId;
    /** 上传人（行级范围判定用） */
    private Long uploadedBy;

    private Long operatorId;
    private String operatorName;
    private LocalDateTime occurredAt;

    /** 变更明细，字段名与 A 的通知行一致 */
    private List<Map<String, Object>> changes;
    /** 面向人的一句话说明（如附件变更、作废原因） */
    private String summary;

    public boolean isVoidOrDelete() {
        return kind == Kind.DELETED || "VOID".equals(status);
    }

    public boolean isAttachmentChange() {
        return kind == Kind.ATTACH_ADDED || kind == Kind.ATTACH_REMOVED;
    }
}
