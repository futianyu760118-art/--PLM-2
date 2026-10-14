package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报告推送日志（AC-B5-6）：成功/失败均记，含订阅 ID、周期区间、生成时间、推送渠道、结果。
 */
@Data
@TableName("plm_report_delivery_log")
public class PlmReportDeliveryLog extends BaseEntity {

    private Long reportId;
    /** INBOX 站内通知 / EMAIL 邮件 */
    private String channel;
    /** SUCCESS / FAILED / SKIPPED（渠道未启用） */
    private String result;
    /** 邮件渠道记录收件地址 */
    private String target;
    private String error;
    private LocalDateTime deliveredAt;
}
