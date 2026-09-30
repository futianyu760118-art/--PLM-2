package com.hjgd.plm.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @TableName("plm_agent_action")
public class AgentAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String actionType;
    private String objectType;
    private String objectId;
    private String payloadJson;
    /**
     * 状态取值：新写入使用 DEH 取值 candidate|pending|confirmed|in_progress|blocked|done|cancelled；
     * 迁移期旧行保留 PROPOSED/APPROVED/REJECTED 可读（对应 candidate/confirmed/cancelled）。
     */
    private String status;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String resultJson;
    private Integer estimatedMinutesSaved;
    private OffsetDateTime createdAt;

    // ---- DEH_CONTRACT_V1 §4 Action 五要素（Owner / 期限 / 状态 / 验证 / 回退升级）----
    /** DEH 稳定动作标识（旧行由增量 34 按主键补 ACT-&lt;id&gt;）。 */
    private String actionId;
    private String decisionId;
    private String goalId;
    private String sourceType;
    private String sourceId;
    private String ownerId;
    private String ownerRole;
    private String description;
    private String expectedResult;
    private String priority;
    private OffsetDateTime startAt;
    private OffsetDateTime dueAt;
    private String verificationRule;
    private String escalationRule;
    private Boolean evidenceRequired;
    /** 最新 Result（与 {@code plm_agent_result.action_id} 互为反向引用）。 */
    private String resultId;
    private String evaluationId;
}
