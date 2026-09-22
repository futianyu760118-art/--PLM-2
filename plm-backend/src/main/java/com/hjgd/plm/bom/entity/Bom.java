package com.hjgd.plm.bom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

@Data
@TableName("plm_bom")
public class Bom extends BaseEntity {

    private String bomNo;
    private String rootPartNo;
    private Long materialId;
    private String versionNo;
    private String status;
    private String bomType;
    private String ecnNo;
    private Integer source;
    private String remark;
    private String createdBy;
}
