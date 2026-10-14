package com.hjgd.plm.subscription.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.subscription.dto.SubscriptionRequest;
import com.hjgd.plm.subscription.dto.SubscriptionVO;
import com.hjgd.plm.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订阅管理（能力点 B3 单条、B4 周期）。
 *
 * <p>所有操作只作用于「我的订阅」：服务层按当前登录用户过滤，
 * 因而 AC-B3-6「多个订阅人互不影响」不需要额外的可见性规则。
 */
@Tag(name = "订阅管理")
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "我的订阅列表（AC-B4-9）")
    @GetMapping("")
    @PreAuthorize("hasAuthority('subscription:view')")
    public Result<PageResult<SubscriptionVO>> page(@RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String status) {
        return Result.success(subscriptionService.page(pageNum, pageSize, type, status));
    }

    @Operation(summary = "新建订阅（单条 AC-B3-1 / 周期 AC-B4-1）")
    @PostMapping("")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<SubscriptionVO> create(@RequestBody SubscriptionRequest request) {
        return Result.success(subscriptionService.create(request));
    }

    @Operation(summary = "修改订阅（改粒度/时点下一完整周期生效，AC-B4-7）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<SubscriptionVO> update(@PathVariable Long id, @RequestBody SubscriptionRequest request) {
        return Result.success(subscriptionService.update(id, request));
    }

    @Operation(summary = "删除订阅（历史报告不受影响，AC-B4-8）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        subscriptionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "暂停/恢复订阅（AC-B4-6）")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<SubscriptionVO> setStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.success(subscriptionService.setStatus(id, status));
    }

    @Operation(summary = "资料详情页的订阅状态（AC-B3-1）")
    @GetMapping("/item/state")
    @PreAuthorize("hasAuthority('subscription:view')")
    public Result<Map<String, Object>> itemState(@RequestParam Long ledgerId) {
        return Result.success(subscriptionService.itemState(ledgerId));
    }

    @Operation(summary = "订阅本条资料（AC-B3-1；无查看权限不可订阅 AC-B3-8）")
    @PostMapping("/item")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<SubscriptionVO> subscribe(@RequestParam Long ledgerId) {
        return Result.success(subscriptionService.subscribeItem(ledgerId));
    }

    @Operation(summary = "退订本条资料（AC-B3-5）")
    @DeleteMapping("/item")
    @PreAuthorize("hasAuthority('subscription:manage')")
    public Result<Void> unsubscribe(@RequestParam Long ledgerId) {
        subscriptionService.unsubscribeItem(ledgerId);
        return Result.success();
    }
}
