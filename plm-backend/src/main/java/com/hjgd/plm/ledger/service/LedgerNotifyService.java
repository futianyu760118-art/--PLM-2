package com.hjgd.plm.ledger.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ledger.entity.PlmNotification;
import com.hjgd.plm.ledger.entity.PlmNotificationSetting;

import java.util.List;
import java.util.Map;

/**
 * 站内通知（AC-A4-7 ~ AC-A4-11）。
 *
 * <p>同步写库而非消费 outbox：AC-A4-9 要求「关联变更后 1 分钟内可见」，
 * 同事务落库天然满足；outbox 的消费方（OutboxPublisherJob）会把已投递事件标为 PUBLISHED，
 * 第二个消费者与其竞争会漏事件，因此不复用该通道。
 *
 * <p>聚合按 {@code 用户 + 类型 + 对象 + 5 分钟窗口} 生成 agg_key，同窗口内多次变更
 * 合并进同一行的 changes 数组（AC-A4-10）。
 */
public interface LedgerNotifyService {

    /** 通知类型（AC-A4-11 按类型开关的取值域） */
    String TYPE_LEDGER_CREATED = "LEDGER_CREATED";
    String TYPE_LEDGER_CHANGED = "LEDGER_CHANGED";
    String TYPE_LEDGER_DELETED = "LEDGER_DELETED";
    String TYPE_LEDGER_LINK = "LEDGER_LINK";
    String TYPE_OBJ_INVALID = "OBJ_INVALID";

    /**
     * 发一条通知（按窗口聚合）。
     *
     * @param userId  接收人
     * @param changes 变更明细 [{field,before,after}]
     * @param extra   额外上下文，可含 objectNo/objectName/ledgerNo/operator/link
     */
    void notify(Long userId, String notifyType, String title, String objectType, String objectId,
                List<Map<String, Object>> changes, Map<String, Object> extra);

    /** 同一对象通知多个接收人（去重、排除操作人自己） */
    void notifyAll(List<Long> userIds, String notifyType, String title, String objectType,
                   String objectId, List<Map<String, Object>> changes, Map<String, Object> extra);

    /** 该用户是否开启该类型通知（AC-A4-11） */
    boolean enabled(Long userId, String notifyType);

    /** 设置开关（AC-A4-11） */
    PlmNotificationSetting setEnabled(Long userId, String notifyType, boolean enabled);

    List<PlmNotificationSetting> settings(Long userId);

    PageResult<PlmNotification> page(Long userId, Integer readStatus, String notifyType,
                                     int pageNum, int pageSize);

    long unreadCount(Long userId);

    void markRead(Long userId, List<Long> ids);

    void markAllRead(Long userId);

    /** 解析出的通知明细（含 changes），供前端展开 */
    Map<String, Object> detail(Long userId, Long id);
}
