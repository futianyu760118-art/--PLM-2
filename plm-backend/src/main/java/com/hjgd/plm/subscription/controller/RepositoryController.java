package com.hjgd.plm.subscription.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资料汇总处（能力点 B6）。
 *
 * <p>路径沿用台账前缀 {@code /ledger}：汇总处就是台账表的一个视图，
 * 前端路由 {@code /ledger/repository} 也一直是这个名字，另起前缀只会让两侧对不上。
 *
 * <p>附件预览/下载仍走台账既有的 {@code /ledger/attachments/{id}/download|preview}
 * （AC-B6-4/B6-5 的权限校验与拒绝留痕已在那边完成），本控制器只负责列表。
 */
@Tag(name = "资料汇总处")
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @Operation(summary = "资料汇总处组合检索（AC-B6-1~B6-9）")
    @GetMapping("/repository")
    @PreAuthorize("hasAuthority('ledger:view')")
    public Result<PageResult<RepositoryItemVO>> repository(LedgerQuery query) {
        return Result.success(repositoryService.page(query));
    }
}
