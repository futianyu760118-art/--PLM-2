package com.hjgd.plm.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题反馈（对齐中台 SPC 反馈模块，含 AI 自动实现）。
 */
@Data
@TableName("plm_feedback")
public class Feedback extends BaseEntity {

    private String type;          // issue/suggestion/inquiry
    private String severity;      // low/medium/high
    private String pagePath;      // 所在页面
    private String title;
    private String description;
    private String attachments;   // JSON 数组 [{key,url,name,size,mime}]
    private String status;        // pending/processing/resolved/closed

    private String reply;         // 管理员回复
    private String repliedBy;
    private LocalDateTime repliedAt;

    private Long submitterId;
    private String submitterName;

    // AI 自动实现
    private String aiStatus;      // none/queued/running/done/failed
    private String aiBranch;
    private String aiNote;
    private String aiRequestedBy;
    private LocalDateTime aiRequestedAt;
    private String aiStage;
    private LocalDateTime aiStageStartedAt;
    private String aiProgress;    // JSON 数组 [{at,kind,stage,text}]
    private String aiResult;      // JSON 对象 {branch,summary,files,gates,runDir,error}
}
