package com.hjgd.plm.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物料参数值(KV)。不继承 BaseEntity：该表无 deleted/created_at 列。
 */
@Data
@TableName("plm_material_param")
public class MaterialParam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private String paramKey;
    private String paramValue;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
