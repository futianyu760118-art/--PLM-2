package com.hjgd.plm.m04.document.dto;

import com.hjgd.plm.common.PageQuery;
import com.hjgd.plm.m04.document.enums.DocLifecycleState;
import com.hjgd.plm.m04.document.enums.DocType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentQueryDTO extends PageQuery {

    /** 编号 / 标题 模糊 */
    private String keyword;

    private DocType docType;

    private DocLifecycleState lifecycleState;

    private String partNo;

    private String projectNo;

    private String owner;

    /** true = 只看被锁定的（检出的） */
    private Boolean locked;
}
