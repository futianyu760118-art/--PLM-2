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
    private String autoNo;
    private String projectName;
    private String partNo;
    private String customerCode;
    private String customerName;
    private String projectType;
    /** 产品分类 V=全新产品 R=功能性能拓展 M=客户定制变化（项目号第 3 段首字母） */
    private String projectClass;
    /** 分类版本号 1-9（项目号第 3 段数字） */
    private Integer classVersion;
    /** 存量非标准后缀（-1/JY/YL），仅迁移记录，新编号不使用 */
    private String codeSuffix;
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
    private String sourceSystem;
    private Integer revision;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
