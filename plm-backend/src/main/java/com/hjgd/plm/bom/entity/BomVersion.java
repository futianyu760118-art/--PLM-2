package com.hjgd.plm.bom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_bom_version")
public class BomVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bomId;
    private String versionNo;
    private String snapshot;
    private String ecnNo;
    private String changeReason;
    private String createdBy;
    private LocalDateTime createdAt;
}
