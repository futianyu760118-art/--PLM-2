package com.hjgd.plm.bom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("plm_bom_item")
public class BomItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bomId;
    private Long parentItemId;
    private String path;
    private Integer levelNo;
    private String parentPartNo;
    private String partNo;
    private Long materialId;
    private String partName;
    private BigDecimal quantity;
    private String materialTexture;
    private String specification;
    private String unit;
    private Integer makeType;
    private String versionNo;
    private Long model3dFileId;
    private Long drawingFileId;
    private Integer sortOrder;
    private String processOp;
    private String sbomClass;
    private String remark;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<BomItem> children;
}
