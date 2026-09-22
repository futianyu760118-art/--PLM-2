package com.hjgd.plm.mold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_mold_trial_log")
public class MoldTrialLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String moldNo;
    private String partNo;
    private LocalDateTime trialTime;
    private Integer trialCount;
    private String defectPhenomenon;
    private String repairPosition;
    private String modifyData;
    private String solution;
    private String handler;
    private LocalDateTime createdAt;
    private Integer deleted;
}
