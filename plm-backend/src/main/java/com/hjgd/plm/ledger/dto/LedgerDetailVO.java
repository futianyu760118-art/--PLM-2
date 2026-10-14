package com.hjgd.plm.ledger.dto;

import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.entity.PlmLedgerAttachment;
import lombok.Data;

import java.util.List;

/** 证据详情（台账视图与资料汇总处共用同一份数据，AC-A3-3）。 */
@Data
public class LedgerDetailVO {

    private PlmLedger ledger;
    private List<PlmLedgerAttachment> attachments;
    private List<LedgerLinkVO> links;
    private Boolean canEdit;
    private Boolean canDelete;
}
