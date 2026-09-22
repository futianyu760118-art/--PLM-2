package com.hjgd.plm.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_archive_file")
public class ArchiveFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private Long archiveNodeId;
    private Long fileId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
