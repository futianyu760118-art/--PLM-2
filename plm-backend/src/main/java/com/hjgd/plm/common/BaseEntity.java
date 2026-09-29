package com.hjgd.plm.common;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 必须给非 null 初值：该字段标注了 FieldFill.INSERT 但 MetaObjectHandler 未填充，
    // 若保持 null，MP 会显式写入 NULL，逻辑删除过滤条件 deleted = 0 随即把新行永久隐藏。
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Integer deleted = 0;
}
