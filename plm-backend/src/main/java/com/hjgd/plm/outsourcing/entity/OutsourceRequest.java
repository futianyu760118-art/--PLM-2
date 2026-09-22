package com.hjgd.plm.outsourcing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_outsource_request")
public class OutsourceRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestNo;
    private String outsourceCompany;
    private String contactPerson;
    private String purpose;
    private String drawingType;
    private Integer validityDays;
    private LocalDateTime expireAt;
    private String description;
    private String applicant;
    private String status;
    private String approver;
    private LocalDateTime approveTime;
    private String approveComment;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
