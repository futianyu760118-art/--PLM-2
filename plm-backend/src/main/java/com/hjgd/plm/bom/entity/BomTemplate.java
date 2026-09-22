package com.hjgd.plm.bom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * BOM 大类模板(如结构件/紧固件/包装/电子)。新建 BOM 时可一键套用。
 */
@Data
@TableName("plm_bom_template")
public class BomTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    /** 结构件/紧固件/包装/电子/标准/自定义 */
    private String category;
    private String description;
    private String productType;
    private Boolean enabled;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 子件(查询时由 Controller 装载) */
    private transient List<BomTemplateItem> items;
}