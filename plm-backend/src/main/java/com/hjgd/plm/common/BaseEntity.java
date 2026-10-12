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

    // 必须带初值: 该字段标注了 fill=INSERT,MyBatis-Plus 会无条件把 deleted 列写入 INSERT 语句,
    // 而 MetaObjectHandler 未填充该字段,初值缺失时会落库为 NULL,导致后续 deleted=0 的查询查不到数据。
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Integer deleted = 0;
}
