package com.hjgd.plm.feedback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈状态流转历史(AC F16a)。只增不改: 每次提交/流转追加一行,
 * 使 plm_feedback 上的单值快照不再覆盖历史轨迹。
 * 该表无 updated_at / deleted 列, 故不继承 BaseEntity。
 */
@Data
@TableName("plm_feedback_transition")
public class FeedbackTransition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属反馈 plm_feedback.id */
    private Long feedbackId;

    /** 变更前状态; 首次提交记录为 null */
    private String fromStatus;

    /** 变更后状态(字典 feedback_status) */
    private String toStatus;

    /** 本次流转的分析结论 / 退回原因; 提交记录为固定文案 */
    private String note;

    /** 立项时回填的 issue 编号, 其余为空 */
    private String relatedIssueNo;

    /** 操作人 sys_user.id */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
