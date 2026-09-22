package com.hjgd.plm.quality.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.quality.enums.InspectionCategory;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_inspection_standard")
public class InspectionStandard {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private Long materialId;
    @EnumValue
    private InspectionCategory category;
    private String itemName;
    private String standardValue;
    private String toleranceRange;
    private String tool;
    private String method;
    private String criteria;
    private String defectDef;
    private String model3dPosition;
    private String drawingVersion;
    private String status;
    private String versionNo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
