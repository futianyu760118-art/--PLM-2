package com.hjgd.plm.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_sync_peer")
public class SyncPeer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String systemCode;
    private String systemName;
    private String baseUrl;
    private String authType;
    private String authSecret;
    private String direction;
    private Integer enabled;
    private LocalDateTime heartbeatAt;
    private String lastCursor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
