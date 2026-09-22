package com.hjgd.plm.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_share_link")
public class ShareLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String shareToken;
    private String partNo;
    private Long model3dId;
    private String title;
    private String creator;
    private LocalDateTime expireAt;
    private Integer maxViews;
    private Integer viewCount;
    private Integer status;
    private Integer allowDownload;
    private String remark;
    private LocalDateTime createdAt;
}
