package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 研发项目供应链品质异常 (EBMS rd_supply_issues -> plm_project_supply_issue) */
@Data
@TableName("plm_project_supply_issue")
public class ProjectSupplyIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate occurDate;
    private String proposer;
    private String productName;
    private String orderNo;
    private String projectNo;
    private String problemDesc;
    private String tempMeasure;
    private String causeAnalysis;
    private String longTermMeasure;
    private LocalDate longTermDate;
    private String responsiblePerson;
    private String responsibleDept;
    private LocalDate planCompleteDate;
    private String audit;
    private Integer closed;
    private String remarks;
    private String sourceSystem;
    private Integer revision;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
