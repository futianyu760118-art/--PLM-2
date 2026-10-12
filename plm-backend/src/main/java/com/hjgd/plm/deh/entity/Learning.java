package com.hjgd.plm.deh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DEH 学习沉淀（{@code plm_learning}，架构 V0.2 §5.7 / F16）。
 *
 * <p>发布三门槛：独立验证、无收益/退化/越权、可版本化回退。回退不删除记录，
 * 置 {@code rolled_back} 并保留 {@code rollbackRef}。
 */
@Data
@TableName("plm_learning")
public class Learning {

    @TableId(type = IdType.INPUT)
    private String learningId;
    private String source;
    private String content;
    private String version;
    /** draft | published | rolled_back | rejected */
    private String status;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String rollbackRef;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
