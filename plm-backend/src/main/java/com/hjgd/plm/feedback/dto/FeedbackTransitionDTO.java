package com.hjgd.plm.feedback.dto;

import lombok.Data;

/** 总控状态流转入参(AC F13) */
@Data
public class FeedbackTransitionDTO {

    /** 目标状态,仅允许 EVALUATING / INITIATED / RETURNED */
    private String targetStatus;

    /** 分析结论 或 退回原因(退回时必须写明不满足的条款与原因) */
    private String note;

    /** 立项 issue 编号,仅 INITIATED 时必填 */
    private String relatedIssueNo;
}
