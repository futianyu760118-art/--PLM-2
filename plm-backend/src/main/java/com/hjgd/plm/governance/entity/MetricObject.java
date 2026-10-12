package com.hjgd.plm.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 指标对象（F13 / ISS-009）：一个指标必须有 Owner、业务定义、版本、有效期、
 * 质量规则与阈值，否则它只是「一列数字」，不能作为管理判据。
 */
@Data
@TableName("plm_metric_object")
public class MetricObject {

    @TableId(type = IdType.INPUT)
    private String metricCode;
    private String name;
    private String businessDefinition;
    /** Accountable Business Owner（角色位，姓名 G0 补录）。 */
    private String ownerRole;
    private String ownerId;
    private String version;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 质量规则，JSON 文本。 */
    private String qualityRule;
    /** 无历史基线时的采集方法（不编造数值）。 */
    private String baselineMethod;
    /** 阈值，JSON 文本；为空表示尚无历史基线。 */
    private String threshold;
    private String status;
    private LocalDateTime createdAt;
}
