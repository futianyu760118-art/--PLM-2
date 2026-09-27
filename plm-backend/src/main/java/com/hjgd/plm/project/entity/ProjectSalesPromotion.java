package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 研发项目销售推广进度 (EBMS rd_sales_promotion -> plm_project_sales_promotion) */
@Data
@TableName("plm_project_sales_promotion")
public class ProjectSalesPromotion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectNo;
    private String productModel;
    private String salesperson;
    private String customer;
    private String appearance;
    private String price;
    private String performance;
    private String functionFeedback;
    private String progress;
    private String remarks;
    private String sourceSystem;
    private Integer revision;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
