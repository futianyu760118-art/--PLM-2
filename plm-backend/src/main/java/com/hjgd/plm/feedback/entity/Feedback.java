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
    // pending/proposed/approved/implementing/implemented/resolved/verified/merged/closed
    private String status;

    private String reply;         // 管理员回复
    private String repliedBy;
    private LocalDateTime repliedAt;

    private Long submitterId;
    private String submitterName;

    // 7 步闭环新增: AI 方案与各节点审计
    private String proposal;      // AI 生成的方案(待提交人确认)
    private LocalDateTime proposalAt;
    private String rejectNote;    // 提交人驳回方案的原因(重新分析用)
    private String approvedBy;    // 确认方案人
    private LocalDateTime approvedAt;
    private String closedBy;      // 确认闭环人(提交人)
    private LocalDateTime closedAt;
    private String verifiedBy;    // 管理员终审人
    private LocalDateTime verifiedAt;
    private String mergedBy;      // 合并人
    private LocalDateTime mergedAt;
    private String mergedBranch;  // 合并的分支

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
