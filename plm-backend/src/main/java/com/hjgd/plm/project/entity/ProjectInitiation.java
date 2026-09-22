package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 研发项目 - 立项申请书 (来源: sales 系统「研发中心-项目管理-立项申请」)
 * 六大区块 + 五阶段审批流 + 批准转项目。
 */
@Data
@TableName("plm_project_initiation")
public class ProjectInitiation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String initNo;
    private Long projectId;

    // 一、基本信息
    private String projectNo;
    private String projectName;
    private String projectType;
    private String startDate;
    private String department;
    private String owner;
    private String cooperators;
    private String otherInfo;

    // 二、客户信息
    private String customerNo;
    private String customerType;
    private String customerLevel;
    private String customerWinRate;
    private String marketStatus;
    private String customerPain;
    private String keySuccess;
    private String hasCompetitor;
    private String purchaseCycle;
    private String devType;

    // 三~六、子表(JSON 文本)
    private String productSpecs;
    private String feasibility;
    private String approvalSigns;
    private String salesForecast;
    private String specialReqs;

    // 研发目标与内容
    private String background;
    private String necessity;
    private String marketAnalysis;
    private String rdObjectives;
    private String rdContent;
    private String keyInnovation;
    private String techSolution;
    private String techRoute;
    private String planSummary;
    private String milestones;
    private String expectedOutcome;
    private String economicBenefit;
    private String targetMarket;
    private BigDecimal budgetTotal;
    private String budgetDetail;
    private String teamRequirement;
    private String riskAnalysis;
    private String riskMeasures;

    // 审批信息
    private String applicant;
    private String applyDate;
    private String approvalStatus;
    private String approver;
    private String approvalDate;
    private String approvalOpinion;

    // 五阶段审批流
    private String workflowStage;
    private String step1ApplyDate;
    private String step1Applicant;
    private String step2Approver;
    private String step2Date;
    private String step2Opinion;
    private String step2Result;
    private String step3RdReviewer;
    private String step3RdOpinion;
    private String step3RdDate;
    private String step3FinanceReviewer;
    private String step3FinanceOpinion;
    private String step3FinanceDate;
    private String step4Approver;
    private String step4Date;
    private String step4Opinion;
    private String step5Owner;
    private String step5StartDate;

    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
