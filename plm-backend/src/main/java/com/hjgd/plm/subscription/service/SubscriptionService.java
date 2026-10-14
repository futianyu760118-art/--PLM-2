package com.hjgd.plm.subscription.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.subscription.dto.SubscriptionRequest;
import com.hjgd.plm.subscription.dto.SubscriptionVO;
import com.hjgd.plm.subscription.entity.PlmSubscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订阅管理（能力点 B3 单条、B4 周期）。
 *
 * <p>单条订阅与周期订阅共用一张表、一套接口，靠 {@code type} 分流：
 * 两者的「上限 / 状态机 / 列表展示」要求不同，但「谁订了什么、怎么改、怎么退」是一致的。
 */
public interface SubscriptionService {

    String TYPE_ITEM = "ITEM";
    String TYPE_PERIOD = "PERIOD";

    String STATUS_ENABLED = "ENABLED";
    String STATUS_PAUSED = "PAUSED";

    // ---------- 我的订阅 ----------

    PageResult<SubscriptionVO> page(Integer pageNum, Integer pageSize, String type, String status);

    SubscriptionVO create(SubscriptionRequest request);

    SubscriptionVO update(Long id, SubscriptionRequest request);

    /** 删除订阅；已发送的历史报告不受影响（AC-B4-8） */
    void delete(Long id);

    /** 暂停/恢复；恢复后从恢复时刻所属的下一个完整周期起生成（AC-B4-6） */
    SubscriptionVO setStatus(Long id, String status);

    // ---------- 单条订阅（AC-B3） ----------

    /** 资料详情页的入口状态：是否已订阅 + 订阅编号（AC-B3-1） */
    Map<String, Object> itemState(Long ledgerId);

    SubscriptionVO subscribeItem(Long ledgerId);

    void unsubscribeItem(Long ledgerId);

    // ---------- 供报告与监听方使用 ----------

    List<PlmSubscription> subscribersOf(Long ledgerId);

    /** 作废/删除后订阅自动失效（AC-B3-7） */
    void invalidateItemSubscriptions(Long ledgerId);

    /** 到期待生成的周期订阅（调度扫描用，AC-B4-3/B5-1） */
    List<PlmSubscription> listDue(LocalDateTime now, int limit);

    /** 计算订阅的下一次生成时点 */
    LocalDateTime nextRunAt(PlmSubscription subscription, LocalDateTime from);

    /** 报告应覆盖的周期区间 [start, end)：上一段已结束的自然日/周/月（AC-B4-2） */
    LocalDateTime[] periodOf(PlmSubscription subscription, LocalDateTime runAt);
}
