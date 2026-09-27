package com.hjgd.plm.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("plm_sync_inbox")
public class SyncInbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private UUID eventId;
    private UUID correlationId;
    private String objectType;
    private String externalKey;
    private String operation;
    private String sourceSystem;
    private Integer sourceRevision;
    private String payloadJson;
    private String status;
    private String error;
    private LocalDateTime receivedAt;
    private LocalDateTime appliedAt;
}
