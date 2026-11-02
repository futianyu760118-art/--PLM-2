package com.hjgd.plm.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 企业微信自建应用消息（按 userid 发给个人，支持按手机号自动匹配 userid）。
 * 配置在 plm_notify_config(channel='wecom_app')：corp_id / agent_id / corp_secret / enabled。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComAppService {

    private static final String API = "https://qyapi.weixin.qq.com/cgi-bin";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private volatile String token;
    private volatile long tokenExpireAt = 0L;

    /** 应用是否已配置并启用。 */
    public boolean enabled() {
        Map<String, Object> cfg = config();
        return cfg != null && "1".equals(String.valueOf(cfg.get("enabled")))
                && hasText(cfg.get("corp_id")) && hasText(cfg.get("corp_secret")) && hasText(cfg.get("agent_id"));
    }

    private Map<String, Object> config() {
        var rows = jdbc.queryForList(
                "SELECT corp_id, agent_id, corp_secret, enabled FROM plm_notify_config WHERE channel='wecom_app' LIMIT 1");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean hasText(Object o) {
        return o != null && !String.valueOf(o).trim().isEmpty();
    }

    private synchronized String accessToken() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpireAt) return token;
        Map<String, Object> cfg = config();
        String corpId = str(cfg.get("corp_id"));
        String secret = str(cfg.get("corp_secret"));
        String url = API + "/gettoken?corpid=" + enc(corpId) + "&corpsecret=" + enc(secret);
        JsonNode n = get(url);
        if (n.path("errcode").asInt(-1) != 0) {
            throw new IllegalStateException("获取 access_token 失败: " + n.path("errmsg").asText() + " (" + n.path("errcode").asInt() + ")");
        }
        token = n.path("access_token").asText();
        int expires = n.path("expires_in").asInt(7200);
        tokenExpireAt = System.currentTimeMillis() + (expires - 300L) * 1000L;
        return token;
    }

    /** 按手机号获取企业微信 userid。 */
    public String useridByMobile(String mobile) {
        if (!hasText(mobile)) return null;
        try {
            String url = API + "/user/getuserid?access_token=" + enc(accessToken()) + "&mobile=" + enc(mobile.trim());
            JsonNode n = get(url);
            if (n.path("errcode").asInt(-1) != 0) {
                log.warn("[企业微信应用] 手机号{} 转userid失败: {}", mobile, n.path("errmsg").asText());
                return null;
            }
            return n.path("userid").asText(null);
        } catch (Exception e) {
            log.warn("[企业微信应用] 手机号{} 转userid异常: {}", mobile, e.getMessage());
            return null;
        }
    }

    /** 发送文本应用消息给单个用户。成功返回 true。 */
    public boolean sendText(String userid, String content) {
        if (!hasText(userid) || !hasText(content)) return false;
        try {
            Map<String, Object> cfg = config();
            String agentId = str(cfg.get("agent_id"));
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("touser", userid);
            body.put("msgtype", "text");
            body.put("agentid", Integer.parseInt(agentId));
            body.put("text", Map.of("content", content));
            body.put("safe", 0);
            String json = objectMapper.writeValueAsString(body);
            String url = API + "/message/send?access_token=" + enc(accessToken());
            JsonNode n = post(url, json);
            int code = n.path("errcode").asInt(-1);
            if (code != 0) {
                // token 可能过期，清缓存重试一次
                if (code == 42001 || code == 40014 || code == 40001) {
                    token = null;
                    n = post(API + "/message/send?access_token=" + enc(accessToken()), json);
                    code = n.path("errcode").asInt(-1);
                }
                if (code != 0) {
                    log.warn("[企业微信应用] 发送给{} 失败: {} ({})", userid, n.path("errmsg").asText(), code);
                    return false;
                }
            }
            log.info("[企业微信应用] 已发送应用消息 -> {}", userid);
            return true;
        } catch (Exception e) {
            log.warn("[企业微信应用] 发送给{} 异常: {}", userid, e.getMessage());
            return false;
        }
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(resp.body());
    }

    private JsonNode post(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(resp.body());
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
