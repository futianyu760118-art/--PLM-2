package com.hjgd.plm.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data @TableName("plm_work_item")
public class WorkItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemNo;
    private String type;
    private String title;
    private String refType;
    private String refId;
    private Integer priority;
    private Long ownerId;
    private String status;
    private LocalDateTime slaDueAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
