package com.hjgd.plm.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Gate 定义（F08）：Node 放行门与 G0-G8 阶段门同表同模型。 */
@Data
@TableName("plm_gate")
public class Gate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String gateCode;
    private String name;
    private Integer seq;
    private String ownerRole;
    private String version;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
