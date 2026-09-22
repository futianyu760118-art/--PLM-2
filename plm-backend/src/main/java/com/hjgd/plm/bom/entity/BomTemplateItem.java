package com.hjgd.plm.bom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("plm_bom_template_item")
public class BomTemplateItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String parentPartNo;
    private String childPartNo;
    private String childName;
    private java.math.BigDecimal quantity;
    private String unit;
    private Boolean isOptional;
    private Integer sortOrder;
    private String remark;
}