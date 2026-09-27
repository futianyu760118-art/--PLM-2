package com.hjgd.plm.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_sync_checkpoint")
public class SyncCheckpoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String systemCode;
    private String objectType;
    private String cursor;
    private LocalDateTime lastRunAt;
    private Integer lagCount;
    private LocalDateTime updatedAt;
}
