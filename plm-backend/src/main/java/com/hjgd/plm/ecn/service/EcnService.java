package com.hjgd.plm.ecn.service;

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

    void reviewL1Approve(Long id, EcnReviewDTO dto);

    void reviewL1Reject(Long id, EcnReviewDTO dto);

    void reviewL2Approve(Long id, EcnReviewDTO dto);

    void reviewL2Reject(Long id, EcnReviewDTO dto);

    void effect(Long id);

    void voidEcn(Long id);

    List<EcnFlowLog> getFlowLogs(Long ecnId);
}
