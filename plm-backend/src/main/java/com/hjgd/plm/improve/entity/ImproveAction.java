package com.hjgd.plm.improve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_improve_action")
public class ImproveAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionNo;
    private Long issueId;
    private String title;
    private String actionType;
    private Long ownerId;
    private LocalDate dueDate;
    private String status;
    private String evidenceUrl;
    private Long standardDocId;
    private LocalDateTime createdAt;
    private LocalDateTime doneAt;
}
