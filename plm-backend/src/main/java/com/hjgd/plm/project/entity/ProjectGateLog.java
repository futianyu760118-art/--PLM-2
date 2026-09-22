package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_project_gate_log")
public class ProjectGateLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String gateCode;
    private String gateName;
    private String result;
    private LocalDate plannedDate;
    private LocalDate actualDate;
    private String operator;
    private String comment;
    private String evidenceRef;
    private LocalDateTime createdAt;
}
