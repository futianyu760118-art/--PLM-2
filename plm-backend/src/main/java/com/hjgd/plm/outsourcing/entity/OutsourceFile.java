package com.hjgd.plm.outsourcing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_outsource_file")
public class OutsourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long fileId;
    private Long watermarkedFileId;
    private Integer downloadCount;
    private LocalDateTime lastDownloadAt;
    private LocalDateTime createdAt;
}
