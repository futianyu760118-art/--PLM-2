package com.hjgd.plm.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_feedback")
public class Feedback extends BaseEntity {

    private String feedbackNo;
    private String type;
    private String content;
    private String expectedResult;
    private String pagePath;
    private String pageHash;
    private String contact;
    private String contentHash;
    private String status;
    private Long submitterId;
    private String relatedIssueNo;
    private String evaluateNote;
    private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
}
