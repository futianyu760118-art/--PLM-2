package com.hjgd.plm.okr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @TableName("plm_okr_objective")
public class OkrObjective {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cycleId;
    private Long orgId;
    private Long ownerId;
    private String title;
    private String level;
    private String status;
    private BigDecimal progressPct;
    private Long parentId;
    private LocalDateTime createdAt;
}
