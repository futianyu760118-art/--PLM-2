package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 订阅（单条 ITEM / 周期 PERIOD 统一表，AC-B3 / AC-B4）。
 *
 * <p>单条订阅用 {@code ledgerId} 指向具体资料（AC-B3-1）；
 * 周期订阅用 {@code filterJson} 存分类过滤条件，按 {@code period/weekday/monthday/pushTime} 到期生成报告（AC-B4）。
 */
@Data
@TableName("plm_subscription")
public class PlmSubscription extends BaseEntity {

    /** 订阅编号 SUB-yyyyMMdd-NNNN */
    private String subNo;
    /** 订阅人 */
    private Long userId;
    /** ITEM 单条 / PERIOD 周期 */
    private String type;
    /** 订阅名称（列表展示，AC-B4-9） */
    private String name;
    /** 单条订阅指向的资料 */
    private Long ledgerId;
    /** 周期订阅分类过滤：{"dims":[{"dimId":1,"valueId":2}]}（AC-B4-3） */
    private String filterJson;
    /** DAY / WEEK / MONTH（AC-B4-1） */
    private String period;
    /** 按周：1-7（周一=1） */
    private Integer weekday;
    /** 按月：1-28 */
    private Integer monthday;
    /** 推送时点（AC-B5-1） */
    private LocalTime pushTime;
    /** ENABLED / PAUSED / DELETED */
    private String status;
    /** 下次生成时点，调度游标（AC-B4-3） */
    private LocalDateTime nextRunAt;
}
