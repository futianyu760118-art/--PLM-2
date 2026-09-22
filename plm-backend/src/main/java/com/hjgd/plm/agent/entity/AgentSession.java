package com.hjgd.plm.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @TableName("plm_agent_session")
public class AgentSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private String agentCode;
    private Long userId;
    private String channel;
    private String contextJson;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
}
