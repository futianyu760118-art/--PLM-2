package com.hjgd.plm.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operator;
    private Long userId;
    private String username;
    private String ip;
    private String device;
    private String operation;
    private String method;
    private String params;
    private Integer result;
    private String errorMsg;
    private String partNo;
    private String fileVersion;
    private Integer costMs;
    private LocalDateTime createdAt;
}
