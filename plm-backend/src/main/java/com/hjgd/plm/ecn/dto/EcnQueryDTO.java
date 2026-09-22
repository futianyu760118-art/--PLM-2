package com.hjgd.plm.ecn.dto;

import com.hjgd.plm.common.PageQuery;
import com.hjgd.plm.ecn.enums.EcnChangeType;
import com.hjgd.plm.ecn.enums.EcnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EcnQueryDTO extends PageQuery {

    private String ecnNo;
    private String partNo;
    private EcnChangeType changeType;
    private EcnStatus status;
    private String applicant;
}
