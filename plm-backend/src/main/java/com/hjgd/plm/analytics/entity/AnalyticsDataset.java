package com.hjgd.plm.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("plm_analytics_dataset")
public class AnalyticsDataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String datasetCode;
    private String name;
    private String sqlText;
    private Integer cacheTtlSec;
    private Boolean enabled;
}
