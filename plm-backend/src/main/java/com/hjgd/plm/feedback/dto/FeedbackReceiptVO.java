package com.hjgd.plm.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** 提交回执(AC F10):唯一反馈编号 + 提交时间 */
@Data
@AllArgsConstructor
public class FeedbackReceiptVO {

    private String feedbackNo;
    private LocalDateTime createdAt;
}
