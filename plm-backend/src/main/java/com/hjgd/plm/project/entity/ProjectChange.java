package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_project_change")
public class ProjectChange {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectNo;
    private String changeNo;
    private String changeType;
    private String reason;
    private String status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
