package com.hjgd.plm.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_share_access_log")
public class ShareAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shareId;
    private String visitorIp;
    private String userAgent;
    private String referer;
    private LocalDateTime accessedAt;
}
