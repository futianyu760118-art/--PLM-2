package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 资料附件（AC-B6-2 的「操作：查看/下载」、AC-B6-3 的在线预览判定） */
@Data
public class RepoFileVO {

    private Long attachmentId;
    private Long fileId;
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    /** pdf / jpg / jpeg / png 可在页面内预览，其余提示下载查看（AC-B6-3） */
    private Boolean previewable;
    private String downloadUrl;
    private String previewUrl;
}
