package com.hjgd.plm.subscription.service.impl;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资料汇总处实现：台账列表 + 条目补全。
 *
 * <p>刻意直接复用 {@code LedgerService.page} 而不是另写一条 SQL：
 * 汇总处一旦有了自己的查询语句，就迟早会与台账在筛选口径或行级范围上分叉，
 * 而 AC-B6-9「仅检索有权限范围」正是靠「只有一套查询」来保证的。
 */
@Service
@RequiredArgsConstructor
public class RepositoryServiceImpl implements RepositoryService {

    private final LedgerService ledgerService;
    private final RepositoryItemAssembler assembler;

    @Override
    public PageResult<RepositoryItemVO> page(LedgerQuery query) {
        PageResult<PlmLedger> page = ledgerService.page(query);
        List<RepositoryItemVO> items = assembler.assemble(page.getRecords(), true);
        return PageResult.of(page.getTotal(), page.getPageNum(), page.getPageSize(), items);
    }
}
