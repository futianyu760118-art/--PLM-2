package com.hjgd.plm.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_sync_object")
public class SyncObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String externalKey;
    private String sourceSystem;
    private String sourceId;
    private String targetSystem;
    private String targetId;
    private Integer revision;
    private String checksum;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime updatedAt;
}
