package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 研发项目复盘 (EBMS rd_project_reviews -> plm_project_review) */
@Data
@TableName("plm_project_review")
public class ProjectReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectNo;
    private String projectName;
    private String goalOriginal;
    private String goalMilestone;
    private String resultHighlights;
    private String resultLowlights;
    private String resultActual;
    private String successFactors;
    private String failureCauses;
    private String insights;
    private String experience;
    private String actionPlan;
    private String remarks;
    private String sourceSystem;
    private Integer revision;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
