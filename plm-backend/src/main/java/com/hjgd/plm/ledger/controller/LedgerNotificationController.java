package com.hjgd.plm.ledger.controller;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.ledger.entity.PlmNotification;
import com.hjgd.plm.ledger.entity.PlmNotificationSetting;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 变更通知（AC-A4-7 ~ AC-A4-11）。
 *
 * <p>只读写当前登录用户自己的通知与开关，不需要额外权限码：
 * 通知是本人私有数据，越权访问在服务层按 userId 归属判定。
 */
@Tag(name = "变更通知")
@RestController
@RequestMapping("/ledger/notification")
@RequiredArgsConstructor
public class LedgerNotificationController {

    private final LedgerNotifyService notifyService;

    @Operation(summary = "我的通知列表")
    @GetMapping("/list")
    public Result<PageResult<PlmNotification>> list(@RequestParam(required = false) Integer readStatus,
                                                    @RequestParam(required = false) String notifyType,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(notifyService.page(SecurityUtils.getCurrentUserId(),
                readStatus, notifyType, page, pageSize));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.success(Map.of("count", notifyService.unreadCount(SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "通知详情（含变更明细）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(notifyService.detail(SecurityUtils.getCurrentUserId(), id));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/read")
    public Result<Void> markRead(@RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    ids.add(Long.valueOf(String.valueOf(o)));
                }
            }
        }
        notifyService.markRead(SecurityUtils.getCurrentUserId(), ids);
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notifyService.markAllRead(SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @Operation(summary = "我的通知开关（按类型，AC-A4-11）")
    @GetMapping("/settings")
    public Result<List<PlmNotificationSetting>> settings() {
        return Result.success(notifyService.settings(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "设置通知开关（AC-A4-11）")
    @PutMapping("/settings")
    public Result<PlmNotificationSetting> setSetting(@RequestBody Map<String, Object> body) {
        String notifyType = body.get("notifyType") == null ? null : String.valueOf(body.get("notifyType"));
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
        return Result.success(notifyService.setEnabled(SecurityUtils.getCurrentUserId(), notifyType, enabled));
    }
}
