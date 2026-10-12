package com.hjgd.plm.feedback.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈出参视图。submitterName 由 sys_user join 取得(不落身份快照);
 * contact 为隐私字段,仅在调用方具备 feedback:manage 时返回(AC F7)。
 */
@Data
public class FeedbackVO {

    private Long id;
    private String feedbackNo;
    private String type;
    private String content;
    private String expectedResult;
    private String pagePath;
    private String contact;
    private String status;
    private Long submitterId;
    private String submitterName;
    private String relatedIssueNo;
    private String evaluateNote;
    private Long evaluatedBy;
    private String evaluatedByName;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 完整状态变更记录(AC F16a), 按时间正序。仅在详情查询(getByNo)时装载;
     * 列表查询为控制开销不装载此字段。
     */
    private List<FeedbackTransitionVO> transitions;
}
