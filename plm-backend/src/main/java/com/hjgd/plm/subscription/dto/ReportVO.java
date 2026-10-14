package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 报告（列表行 / AC-B5-7 历史报告回溯） */
@Data
public class ReportVO {

    private Long id;
    private String reportNo;
    private Long subscriptionId;
    private String subName;
    private String type;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer itemCount;
    private String status;
    private Integer retryCount;
    private String error;
    private LocalDateTime generatedAt;
    private LocalDateTime pushedAt;
    /** 是否有 Excel 明细可下载（AC-B5-7） */
    private Boolean hasExcel;
}
