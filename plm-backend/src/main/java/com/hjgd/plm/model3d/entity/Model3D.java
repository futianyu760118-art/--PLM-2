package com.hjgd.plm.model3d.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.model3d.enums.Model3DType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_model3d")
public class Model3D {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private String modelName;
    @EnumValue
    private Model3DType modelType;
    private String sourceFormat;
    private Long intranetFileId;
    private Long extranetFileId;
    private String thumbnail;
    private String explodeJson;
    private String versionNo;
    private String ecnNo;
    private String status;
    private Integer source;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
