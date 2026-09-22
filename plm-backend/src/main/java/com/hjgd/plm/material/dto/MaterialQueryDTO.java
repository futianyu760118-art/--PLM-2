package com.hjgd.plm.material.dto;

import com.hjgd.plm.common.PageQuery;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.enums.MaterialType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialQueryDTO extends PageQuery {

    private String partNo;
    private String materialName;
    private MaterialType materialType;
    private MaterialStatus status;
    private String productSeries;
    private String projectNo;
}
