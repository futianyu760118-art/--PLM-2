package com.hjgd.plm.process.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_process_step")
public class ProcessStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Integer stepOrder;
    private String name;
    private Long ownerId;
    private Integer slaHours;
    private String status;
    private Long workItemId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String remark;
}
