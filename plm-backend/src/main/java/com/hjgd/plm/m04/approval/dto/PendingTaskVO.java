package com.hjgd.plm.m04.approval.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办项（待办箱）。
 */
@Data
public class PendingTaskVO {

    private Long taskId;
    private Long instanceId;
    private String objectType;
    private String objectNo;
    private String flowName;
    private Integer nodeSeq;
    private String nodeName;
    private String approver;
    private LocalDateTime createdAt;
    /** 距提交已过小时数，页面用来标红压单 */
    private Long waitingHours;
}
