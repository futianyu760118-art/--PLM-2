package com.hjgd.plm.m04.ecr.service;

import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.m04.ecr.dto.EcrDTO;
import com.hjgd.plm.m04.ecr.dto.EcrQueryDTO;
import com.hjgd.plm.m04.ecr.entity.Ecr;

public interface EcrService {

    PageResult<Ecr> page(EcrQueryDTO query);

    Ecr getById(Long id);

    Ecr create(EcrDTO dto);

    Ecr update(EcrDTO dto);

    void delete(Long id);

    /** 提交审批（走 ECR_DEFAULT 流程） */
    void submit(Long id);

    /** 审批动作：通过/驳回/退回 */
    void approve(Long id, ApprovalAction action, String comment);

    /** 审批通过后一键生成 ECN，并把 ECR 置为 CONVERTED */
    Ecr toEcn(Long id);

    ApprovalProgressVO progress(Long id);

    void close(Long id);
}
