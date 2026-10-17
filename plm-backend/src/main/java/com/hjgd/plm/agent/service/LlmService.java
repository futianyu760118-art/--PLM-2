package com.hjgd.plm.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 大模型调用服务（OpenAI 兼容 /chat/completions）。
 * 配置: PLM_LLM_BASE_URL / PLM_LLM_API_KEY / PLM_LLM_MODEL（均为环境变量，缺 key 时明确返回 null，绝不伪造回复）。
 */
@Slf4j
@Service
public class LlmService {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlmService(@Value("${plm.llm.base-url:}") String baseUrl,
                      @Value("${plm.llm.api-key:}") String apiKey,
                      @Value("${plm.llm.model:}") String model) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    /** 是否已配置可用的模型 */
    public boolean enabled() {
        return !apiKey.isEmpty() && !baseUrl.isEmpty() && !model.isEmpty();
    }

    public String model() {
        return model;
    }

    /**
     * 单轮对话。system 为角色设定，user 为用户输入（可携带工具数据）。
     * 未配置/调用失败返回 null（调用方回落模板回复）。
     */
    public String chat(String system, String user) {
        if (!enabled()) return null;
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system == null ? "" : system),
                            Map.of("role", "user", "content", user == null ? "" : user)),
                    "temperature", 0.3);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[LLM] 调用失败 HTTP {} : {}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
                return null;
            }
            return extractContent(resp.body());
        } catch (Exception e) {
            log.warn("[LLM] 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /** 从 OpenAI 兼容响应中取 choices[0].message.content（轻量解析，避免引入 JSON 库依赖差异） */
    private String extractContent(String body) {
        int choices = body.indexOf("\"choices\"");
        if (choices < 0) return null;
        int content = body.indexOf("\"content\"", choices);
        if (content < 0) return null;
        int colon = body.indexOf(':', content);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
        if (i >= body.length() || body.charAt(i) != '"') return null;
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char n = body.charAt(i + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 5 < body.length()) {
                            sb.append((char) Integer.parseInt(body.substring(i + 2, i + 6), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(n);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private String json(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof List<?> list) {
                sb.append('[');
                boolean f2 = true;
                for (Object o : list) {
                    if (!f2) sb.append(',');
                    f2 = false;
                    if (o instanceof Map) sb.append(json((Map<String, Object>) o));
                    else sb.append('"').append(String.valueOf(o)).append('"');
                }
                sb.append(']');
            } else if (v instanceof Map) {
                sb.append(json((Map<String, Object>) v));
            } else {
                sb.append('"').append(String.valueOf(v)).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
