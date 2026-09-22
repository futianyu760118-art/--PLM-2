package com.hjgd.plm.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_file")
public class PlmFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String filePath;
    private String fileUrl;
    private String fileExt;
    private Long fileSize;
    private String fileType;
    private String md5Hash;
    private String visibility;
    private Integer hasWatermark;
    private LocalDateTime expireAt;
    private String partNo;
    private String versionNo;
    private Integer obsolete;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
