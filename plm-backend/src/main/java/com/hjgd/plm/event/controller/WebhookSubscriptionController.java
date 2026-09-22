package com.hjgd.plm.event.controller;

import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "V1 Webhook订阅")
@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
public class WebhookSubscriptionController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "订阅列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT id, name, target_url, event_types, enabled, created_at FROM plm_webhook_subscription ORDER BY id DESC"));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "创建订阅")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody SubReq req) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO plm_webhook_subscription(name, target_url, secret, event_types, enabled) VALUES(?,?,?,?,true)",
                    req.getName(), req.getTargetUrl(), req.getSecret(), req.getEventTypes());
            return Result.success(Map.of("status", "created"));
        } catch (Exception e) {
            return Result.failed("创建失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除订阅")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            jdbcTemplate.update("DELETE FROM plm_webhook_subscription WHERE id=?", id);
        } catch (Exception ignored) {
        }
        return Result.success();
    }

    @Data
    public static class SubReq {
        private String name;
        private String targetUrl;
        private String secret;
        private String eventTypes;
    }
}
