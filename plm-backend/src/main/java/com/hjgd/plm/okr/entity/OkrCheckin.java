package com.hjgd.plm.okr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @TableName("plm_okr_checkin")
public class OkrCheckin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long krId;
    private LocalDate checkinDate;
    private BigDecimal value;
    private Integer confidence;
    private String note;
    private Long userId;
    private LocalDateTime createdAt;
}
