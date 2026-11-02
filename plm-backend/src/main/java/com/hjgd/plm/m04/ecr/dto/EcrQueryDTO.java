package com.hjgd.plm.m04.ecr.dto;

import com.hjgd.plm.common.PageQuery;
import com.hjgd.plm.m04.ecr.enums.EcrChangeType;
import com.hjgd.plm.m04.ecr.enums.EcrStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EcrQueryDTO extends PageQuery {

    private String ecrNo;
    private String partNo;
    private String projectNo;
    private EcrChangeType changeType;
    private EcrStatus status;
    private String applicant;
}
