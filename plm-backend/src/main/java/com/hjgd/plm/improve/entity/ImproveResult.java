package com.hjgd.plm.improve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 改善成效：回写某 issue 对某 KPI 的前后差值，验证改善是否真有效。
 */
@Data
@TableName("plm_improve_result")
public class ImproveResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private String kpiCode;
    private BigDecimal beforeValue;
    private BigDecimal afterValue;
    private LocalDate windowFrom;
    private LocalDate windowTo;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private Boolean effective;
}
