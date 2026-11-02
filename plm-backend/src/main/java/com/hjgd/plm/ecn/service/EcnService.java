package com.hjgd.plm.ecn.service;

import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ecn.dto.EcnDTO;
import com.hjgd.plm.ecn.dto.EcnQueryDTO;
import com.hjgd.plm.ecn.dto.EcnReviewDTO;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.entity.EcnFlowLog;

import java.util.List;

public interface EcnService {

    PageResult<Ecn> page(EcnQueryDTO query);

    Ecn getById(Long id);

    Ecn getByEcnNo(String ecnNo);

    Ecn create(EcnDTO dto);

    Ecn update(EcnDTO dto);

    void delete(Long id);

    void submit(Long id);

    /**
     * 通用审批动作：流程节点、级数、驳回走向全部由 plm_approval_* 配置决定。
     * 无审批实例的在途单自动回落旧的两级逻辑。
     */
    void approve(Long id, ApprovalAction action, String comment);

    /** 审批进度（节点 + 动作流水） */
    ApprovalProgressVO progress(Long id);

    void reviewL1Approve(Long id, EcnReviewDTO dto);

    void reviewL1Reject(Long id, EcnReviewDTO dto);

    void reviewL2Approve(Long id, EcnReviewDTO dto);

    void reviewL2Reject(Long id, EcnReviewDTO dto);

    void effect(Long id);

    void voidEcn(Long id);

    List<EcnFlowLog> getFlowLogs(Long ecnId);
}
