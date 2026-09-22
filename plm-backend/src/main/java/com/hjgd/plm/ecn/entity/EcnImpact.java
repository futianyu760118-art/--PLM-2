package com.hjgd.plm.ecn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_ecn_impact")
public class EcnImpact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ecnId;
    private String ecnNo;
    private String impactType;
    private String targetType;
    private String targetId;
    private String targetRef;
    private String actionCode;
    private String status;
    private LocalDateTime handledAt;
    private String resultJson;
    private LocalDateTime createdAt;
}
