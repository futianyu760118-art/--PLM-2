package com.hjgd.plm.subscription.dto;

import com.hjgd.plm.ledger.dto.CategoryFilter;
import lombok.Data;

/** 订阅的新建/修改入参（AC-B3、AC-B4） */
@Data
public class SubscriptionRequest {

    /** ITEM 单条订阅 / PERIOD 周期订阅（AC-B3、AC-B4） */
    private String type;
    private String name;
    /** 单条订阅必填：被订阅的资料 id（AC-B3-1） */
    private Long ledgerId;
    /** 周期订阅的报告范围分类条件；为空表示本部门全部资料（AC-B4-3） */
    private CategoryFilter filter;
    /** DAY / WEEK / MONTH（AC-B4-1） */
    private String period;
    /** 按周时的星期几，1=周一 … 7=周日（AC-B4-1） */
    private Integer weekday;
    /** 按月时的日期，1–28（AC-B4-1，取 28 以内保证每月都存在） */
    private Integer monthday;
    /** 推送时点 HH:mm，默认 08:00（AC-B5-1） */
    private String pushTime;
    /** ENABLED / PAUSED（AC-B4-6）；删除走 DELETE 接口单独处理（AC-B4-8） */
    private String status;
}
