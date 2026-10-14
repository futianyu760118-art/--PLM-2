package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 订阅列表行（AC-B4-9：订阅名称、周期、下次生成时间、状态） */
@Data
public class SubscriptionVO {

    private Long id;
    private String subNo;
    private String type;
    private String name;
    private Long ledgerId;
    private String ledgerNo;
    private String ledgerName;
    /** 周期订阅的分类条件，回显给编辑表单 */
    private com.hjgd.plm.ledger.dto.CategoryFilter filter;
    /** 周期订阅的分类条件，人可读文案（列表直接展示） */
    private String filterText;
    private String period;
    private Integer weekday;
    private Integer monthday;
    private String pushTime;
    private String status;
    private LocalDateTime nextRunAt;
    private LocalDateTime createdAt;
}
