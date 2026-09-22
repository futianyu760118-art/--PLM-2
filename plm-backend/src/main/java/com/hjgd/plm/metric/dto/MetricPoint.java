package com.hjgd.plm.metric.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个指标在某一日的计算结果（日粒度快照）。
 * value_calc 为最终展示值；value_num/value_den 保留分子分母便于下钻与对账。
 */
@Data
@Builder
public class MetricPoint {
    private String metricCode;
    private BigDecimal valueNum;
    private BigDecimal valueDen;
    private BigDecimal valueCalc;
    private String dimNote;
}
