package com.hjgd.plm.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_material_version")
public class MaterialVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String partNo;
    private String versionNo;
    private String snapshot;
    private String changeReason;
    private String ecnNo;
    private String createdBy;
    private LocalDateTime createdAt;
}
