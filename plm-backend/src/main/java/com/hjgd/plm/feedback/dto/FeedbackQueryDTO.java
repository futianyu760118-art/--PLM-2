package com.hjgd.plm.feedback.dto;

import com.hjgd.plm.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 总控反馈列表筛选入参(AC F16) */
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackQueryDTO extends PageQuery {

    private String status;
    private String type;
    /** 提交时间起(含),格式 yyyy-MM-dd */
    private String dateFrom;
    /** 提交时间止(含),格式 yyyy-MM-dd */
    private String dateTo;
}
