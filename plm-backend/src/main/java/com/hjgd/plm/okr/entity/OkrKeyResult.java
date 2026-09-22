package com.hjgd.plm.okr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @TableName("plm_okr_key_result")
public class OkrKeyResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long objectiveId;
    private String title;
    private String metricCode;
    private String kpiCode;
    private BigDecimal baseline;
    private BigDecimal target;
    private BigDecimal currentValue;
    private String unit;
    private BigDecimal weight;
    private String status;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
}
