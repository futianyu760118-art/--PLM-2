package com.hjgd.plm.mold.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.mold.enums.MoldStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("plm_mold")
public class Mold {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String moldNo;
    private String partNo;
    private String moldName;
    private LocalDate openDate;
    private Integer cavityCount;
    private Long accumulateShots;
    private Integer maintenanceCycle;
    private LocalDate lastMaintenanceDate;
    private String polishRecord;
    private String insertReplaceRecord;
    private String repairRecord;
    private LocalDate scrapDate;
    @EnumValue
    private MoldStatus status;
    private BigDecimal assetValue;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
