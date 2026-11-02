package com.hjgd.plm.system.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.DataScope;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.common.WeComNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 通知渠道配置（企业微信群机器人）。 */
@Tag(name = "系统-通知配置")
@RestController
@RequestMapping("/system/notify")
@RequiredArgsConstructor
public class NotifyConfigController {

    private final JdbcTemplate jdbc;
    private final WeComNotifyService weComNotifyService;

    @Operation(summary = "读取企业微信通知配置")
    @GetMapping("/wecom")
    public Result<Map<String, Object>> get() {
        requireAdmin();
        var rows = jdbc.queryForList(
                "SELECT webhook_url, enabled FROM plm_notify_config WHERE channel='wecom' LIMIT 1");
        Map<String, Object> m = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            m.put("webhookUrl", "");
            m.put("enabled", false);
        } else {
            var r = rows.get(0);
            m.put("webhookUrl", r.get("webhook_url") == null ? "" : r.get("webhook_url"));
            Object e = r.get("enabled");
            m.put("enabled", e instanceof Number n && n.intValue() == 1);
        }
        return Result.success(m);
    }

    @Operation(summary = "保存企业微信通知配置")
    @PutMapping("/wecom")
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        requireAdmin();
        String url = body.get("webhookUrl") == null ? "" : String.valueOf(body.get("webhookUrl")).trim();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled")) || "true".equals(String.valueOf(body.get("enabled")));
        int count = jdbc.update(
                "UPDATE plm_notify_config SET webhook_url=?, enabled=?, updated_at=now() WHERE channel='wecom'",
                url, enabled ? 1 : 0);
        if (count == 0) {
            jdbc.update("INSERT INTO plm_notify_config(channel, webhook_url, enabled) VALUES ('wecom',?,?)",
                    url, enabled ? 1 : 0);
        }
        return Result.success();
    }

    @Operation(summary = "发送测试消息")
    @PostMapping("/wecom/test")
    public Result<String> test() {
        requireAdmin();
        weComNotifyService.send("【PLM】企业微信通知测试成功 " + java.time.LocalDateTime.now());
        return Result.success("已发送(如未收到请检查 webhook 与启用状态)");
    }

    private void requireAdmin() {
        if (!DataScope.admin()) throw new BusinessException("仅管理员可配置通知");
    }
}
