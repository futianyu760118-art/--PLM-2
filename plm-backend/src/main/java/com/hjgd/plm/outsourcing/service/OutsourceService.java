package com.hjgd.plm.outsourcing.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.outsourcing.dto.OutsourceRequestDTO;
import com.hjgd.plm.outsourcing.entity.OutsourceFile;
import com.hjgd.plm.outsourcing.entity.OutsourceRequest;

import java.util.List;

public interface OutsourceService {

    PageResult<OutsourceRequest> page(Integer pageNum, Integer pageSize, String company, String status);

    OutsourceRequest getById(Long id);

    OutsourceRequest create(OutsourceRequestDTO dto);

    void submit(Long id);

    void approve(Long id, String comment);

    void reject(Long id, String comment);

    List<OutsourceFile> getFiles(Long requestId);

    void download(Long requestId, Long fileId);
}
