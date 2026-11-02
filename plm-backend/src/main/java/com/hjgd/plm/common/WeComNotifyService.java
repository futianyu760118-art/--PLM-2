package com.hjgd.plm.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 企业微信群机器人通知。webhook 配置在 plm_notify_config(channel='wecom')，
 * 未配置或禁用时静默跳过（不影响业务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComNotifyService {

    private final JdbcTemplate jdbc;

    public void send(String content) {
        try {
            var rows = jdbc.queryForList(
                    "SELECT webhook_url, enabled FROM plm_notify_config WHERE channel='wecom' LIMIT 1");
            if (rows.isEmpty()) return;
            var r = rows.get(0);
            Object enabled = r.get("enabled");
            String url = r.get("webhook_url") == null ? "" : String.valueOf(r.get("webhook_url")).trim();
            if (!(enabled instanceof Number n && n.intValue() == 1) || url.isEmpty()) return;

            String body = "{\"msgtype\":\"text\",\"text\":{\"content\":"
                    + quote(content) + "}}";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("[企业微信通知] {} -> {}", resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("[企业微信通知] 发送失败: {}", e.getMessage());
        }
    }

    private String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
