package com.hjgd.plm.improve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_analytics_insight")
public class Insight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String insightNo;
    private String source;
    private String title;
    private String severity;
    private String category;
    private String findingJson;
    private String rootCauseJson;
    private String recommendationJson;
    private String relatedKpiCodes;
    private String relatedObjectRefs;
    private String status;
    private Long agentSessionId;
    private LocalDateTime createdAt;
}
