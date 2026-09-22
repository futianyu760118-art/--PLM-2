package com.hjgd.plm.exportx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_export_task")
public class ExportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String exportNo;
    private String exportType;
    private String format;
    private String name;
    private String specJson;
    private String status;
    private String filePath;
    private Integer rowCount;
    private String errorMsg;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
