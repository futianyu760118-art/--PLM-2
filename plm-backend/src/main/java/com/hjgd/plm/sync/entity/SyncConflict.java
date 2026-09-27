package com.hjgd.plm.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_sync_conflict")
public class SyncConflict {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String externalKey;
    private String fieldName;
    private String localValue;
    private String remoteValue;
    private LocalDateTime localTs;
    private LocalDateTime remoteTs;
    private Integer localRevision;
    private Integer remoteRevision;
    private String resolution;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String note;
    private LocalDateTime createdAt;
}
