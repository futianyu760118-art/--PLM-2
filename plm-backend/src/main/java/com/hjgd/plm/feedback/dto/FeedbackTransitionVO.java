package com.hjgd.plm.feedback.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈状态流转历史出参(AC F16a)。
 * 只带状态值不带中文标签, 显示文案由前端按同一份字典映射, 避免两处维护。
 */
@Data
public class FeedbackTransitionVO {

    private Long id;
    private String fromStatus;
    private String toStatus;
    private String note;
    private String relatedIssueNo;
    private Long operatorId;
    /** 操作人姓名, 由 sys_user 取名, 不落身份快照 */
    private String operatorName;
    private LocalDateTime createdAt;
}
