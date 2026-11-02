package com.hjgd.plm.m04.approval.dto;

import lombok.Data;

/**
 * 审批节点 DTO。seq 由后端按顺序重排，前端不用管。
 */
@Data
public class ApprovalNodeDTO {

    private Long id;
    private Integer seq;
    private String nodeCode;
    private String nodeName;
    /** ROLE / USER / POSITION / PROJECT_ROLE / DEPT_LEADER */
    private String approverType;
    /** 角色码或用户名，多个用逗号分隔 */
    private String approverValue;
    /** 例：change_type='MATERIAL' */
    private String conditionExpr;
    private String parallelGroup;
    private Integer timeoutHours;
    /** BACK / TERMINATE / TO_APPLICANT */
    private String onReject;
    /** REMIND / AUTO_PASS / ESCALATE */
    private String onTimeout;
    private String remark;
}
