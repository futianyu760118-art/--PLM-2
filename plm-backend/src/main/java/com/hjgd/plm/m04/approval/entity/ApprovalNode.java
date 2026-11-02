package com.hjgd.plm.m04.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 审批节点。seq 从 1 递增，同一 parallel_group 的节点并行会签。
 */
@Data
@TableName("plm_approval_node")
public class ApprovalNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private Integer seq;
    private String nodeCode;
    private String nodeName;
    /** ROLE / USER / POSITION / PROJECT_ROLE / DEPT_LEADER */
    private String approverType;
    private String approverValue;
    /** 形如 change_type='MATERIAL'，不满足则该节点在实例中被跳过 */
    private String conditionExpr;
    private String parallelGroup;
    private Integer timeoutHours;
    /** BACK / TERMINATE / TO_APPLICANT */
    private String onReject;
    /** REMIND / AUTO_PASS / ESCALATE */
    private String onTimeout;
    private String remark;
}
