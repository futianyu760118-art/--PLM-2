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
    /** 最近写入 plm_bom_version 快照的版本号, 发布/归档/ECN 升版时更新 */
    private String archiveVersionNo;
    private Integer source;
    private String remark;
    private String createdBy;
}
