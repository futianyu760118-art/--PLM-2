package com.hjgd.plm.mold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("plm_injection_sop")
public class InjectionSop {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private String moldNo;
    private String materialSpec;
    private BigDecimal meltTemp;
    private BigDecimal moldTemp;
    private BigDecimal injectionPressure;
    private BigDecimal packingPressure;
    private BigDecimal injectionSpeed;
    private BigDecimal coolingTime;
    private BigDecimal shrinkageComp;
    private BigDecimal sprueRatio;
    private String defectSolutions;
    private String status;
    private String versionNo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
