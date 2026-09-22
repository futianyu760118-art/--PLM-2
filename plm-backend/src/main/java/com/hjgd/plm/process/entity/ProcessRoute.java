package com.hjgd.plm.process.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_process_route")
public class ProcessRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeNo;
    private String routeName;
    private String refType;
    private String refId;
    private String status;
    private Long currentStepId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
