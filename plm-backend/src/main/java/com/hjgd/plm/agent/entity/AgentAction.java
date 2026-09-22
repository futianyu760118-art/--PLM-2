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
    private String status;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String resultJson;
    private Integer estimatedMinutesSaved;
    private OffsetDateTime createdAt;
}
