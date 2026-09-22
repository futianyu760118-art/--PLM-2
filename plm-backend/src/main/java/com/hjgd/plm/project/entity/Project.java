package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectNo;
    private String projectName;
    private String partNo;
    private String customerCode;
    private String customerName;
    private String projectType;
    private String projectLevel;
    private String urgency;
    private String owner;
    private String department;
    private LocalDate startDate;
    private LocalDate targetDate;
    private LocalDate closeDate;
    private String currentGate;
    private String gateStatus;
    private BigDecimal projectAmount;
    private BigDecimal orderAmount;
    private BigDecimal investAmount;
    private String annualOrder;
    private LocalDate marketDate;
    private String status;
    private String riskLevel;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
