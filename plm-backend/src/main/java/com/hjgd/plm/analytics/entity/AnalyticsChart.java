package com.hjgd.plm.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("plm_analytics_chart")
public class AnalyticsChart {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chartCode;
    private String title;
    private String datasetCode;
    private String chartType;
    private String encodeJson;
    private String defaultFilters;
    private String refreshCron;
    private String ownerRole;
    private Boolean enabled;
}
