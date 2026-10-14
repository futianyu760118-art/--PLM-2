package com.hjgd.plm.subscription.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;

/**
 * 资料汇总处查询（能力点 B6）。
 *
 * <p>汇总处不是第二套数据：筛选走台账同一支查询、行级范围沿用台账同一套规则，
 * 本层只负责把台账行补成「带分类标签、附件、下载地址」的条目（AC-B6-2）。
 * 这样 AC-B6-7「与台账一致」不靠同步、不靠定时对齐，而是结构上不可能不一致。
 */
public interface RepositoryService {

    /** 资料汇总处组合筛选分页（AC-B6-1/2/8/9） */
    PageResult<RepositoryItemVO> page(LedgerQuery query);
}
