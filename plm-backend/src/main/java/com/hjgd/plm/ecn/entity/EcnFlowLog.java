package com.hjgd.plm.ecn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_ecn_flow_log")
public class EcnFlowLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ecnId;
    private Integer step;
    private String action;
    private String operator;
    private String operatorRole;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private LocalDateTime createdAt;
}
