package com.hjgd.plm.improve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_issue")
public class Issue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String issueNo;
    private String sourceType;
    private String sourceRef;
    private String objectType;
    private String objectId;
    private String title;
    private String description;
    private String category;
    private String severity;
    private String status;
    private Long ownerId;
    private Long deptId;
    private LocalDate dueDate;
    private String leanWasteTag;
    private String kpiCodes;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
