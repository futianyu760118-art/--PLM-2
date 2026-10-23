package com.hjgd.plm.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AEOS Hub 能力调用服务：PLM 作为跨系统「请求方」，通过 Hub 借调只读能力（如中台 query_control_platform）。
 * <p>统一工具 hub.query：不直连中台、不为每个中台接口写死；只依赖 Hub 的能力调用 API 与借调轮询。</p>
 * <p>安全：系统令牌仅从环境变量读取；不调用写能力；查询失败返回 error.code，不伪造结果。</p>
 */
@Slf4j
@Service
public class HubInvocationService {

    private final String baseUrl;
    private final String systemId;
    private final String systemToken;
    private final String contractVersion;
    private final String fromAgentId;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;
    private final boolean insecure;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public HubInvocationService(
            @Value("${plm.hub.base-url:}") String baseUrl,
            @Value("${plm.hub.system-id:}") String systemId,
            @Value("${plm.hub.system-token:}") String systemToken,
            @Value("${plm.hub.contract-version:4.0}") String contractVersion,
            @Value("${plm.hub.from-agent-id:plm-agent-orch}") String fromAgentId,
            @Value("${plm.hub.poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${plm.hub.poll-timeout-ms:30000}") long pollTimeoutMs,
            @Value("${plm.hub.insecure:true}") boolean insecure) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.systemId = systemId == null ? "" : systemId.trim();
        this.systemToken = systemToken == null ? "" : systemToken.trim();
        this.contractVersion = contractVersion == null ? "" : contractVersion.trim();
        this.fromAgentId = fromAgentId == null ? "" : fromAgentId.trim();
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000;
        this.pollTimeoutMs = pollTimeoutMs > 0 ? pollTimeoutMs : 30000;
        this.insecure = insecure;
        this.http = buildHttpClient();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty() && !systemId.isEmpty() && !systemToken.isEmpty();
    }

    /**
     * 统一工具 hub.query：调用 Hub 能力并轮询到完成，返回结果对象（如 items/total/data_as_of）。
     *
     * @param capability 目标系统的只读能力名（如 query_control_platform）
     * @param path       能力内的 path（如 /external/orders/list）
     * @param params     能力参数（如 {page:1,page_size:5}）
     */
    public Map<String, Object> query(String capability, String path, Map<String, Object> params) {
        if (!enabled()) {
            throw new BusinessException("hub.query 未配置（缺少 AEOS_HUB_BASE_URL / AEOS_HUB_SYSTEM_ID / AEOS_HUB_SYSTEM_TOKEN）");
        }
        if (capability == null || capability.isBlank()) {
            throw new BusinessException("hub.query 缺少 capability");
        }
        if (path == null || path.isBlank()) {
            throw new BusinessException("hub.query 缺少 path");
        }
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("capability", capability);
        Map<String, Object> capParams = new LinkedHashMap<>();
        capParams.put("path", path);
        capParams.put("params", params == null ? Map.of() : params);
        invocation.put("params", capParams);
        invocation.put("fromAgentId", fromAgentId);

        String borrowOrderId = postInvocation(invocation);
        return pollOrder(borrowOrderId);
    }

    /** POST /api/system/capability-invocations，返回 borrowOrderId。 */
    private String postInvocation(Map<String, Object> body) {
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/api/system/capability-invocations";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-aeos-system-id", systemId)
                    .header("x-aeos-system-token", systemToken)
                    .header("X-AEOS-Contract-Version", contractVersion)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new BusinessException("hub.query 能力调用失败 HTTP " + resp.statusCode() + ": " + ellipsis(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            String borrowOrderId = firstText(root, "borrowOrderId", "orderId", "borrow_order_id");
            if (borrowOrderId == null || borrowOrderId.isBlank()) {
                throw new BusinessException("hub.query 未返回 borrowOrderId: " + ellipsis(resp.body()));
            }
            log.info("[Hub] 能力调用 capability={} requestId={} borrowOrderId={} runId={} status={}",
                    body.get("capability"),
                    firstText(root, "requestId"),
                    borrowOrderId,
                    firstText(root, "runId"),
                    firstText(root, "status"));
            return borrowOrderId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Hub] 能力调用异常: {}", e.getMessage());
            throw new BusinessException("hub.query 能力调用异常: " + e.getMessage());
        }
    }

    /** GET /api/system/borrow-orders/{id}，轮询到 completed/failed 或超时。 */
    private Map<String, Object> pollOrder(String borrowOrderId) {
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        String lastStatus = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                String url = baseUrl.replaceAll("/+$", "") + "/api/system/borrow-orders/" + borrowOrderId;
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("x-aeos-system-id", systemId)
                        .header("x-aeos-system-token", systemToken)
                        .header("X-AEOS-Contract-Version", contractVersion)
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    throw new BusinessException("hub.query 借调查询失败 HTTP " + resp.statusCode());
                }
                JsonNode root = mapper.readTree(resp.body());
                String status = firstText(root, "status", "state");
                lastStatus = status == null ? "" : status;
                if ("completed".equalsIgnoreCase(status)) {
                    JsonNode result = findNode(root, "result");
                    if (result == null || result.isNull()) result = findNode(root, "data");
                    if (result == null || result.isNull()) result = root;
                    log.info("[Hub] 借调完成 borrowOrderId={} status=completed", borrowOrderId);
                    return mapper.convertValue(result, Map.class);
                }
                if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                        || "error".equalsIgnoreCase(status)) {
                    String errCode = firstText(root, "errorCode", "code");
                    String errMsg = firstText(root, "error", "message");
                    throw new BusinessException("hub.query 借调失败[" + (errCode == null ? status : errCode) + "]: " + (errMsg == null ? "" : errMsg));
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[Hub] 借调轮询异常: {}", e.getMessage());
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BusinessException("hub.query 轮询被中断");
            }
        }
        throw new BusinessException("hub.query 借调超时（" + pollTimeoutMs + "ms），最后状态: " + lastStatus);
    }

    /** 从 JSON 节点中按候选字段顺序取第一个非空文本值；会递归搜索顶层与常见信封字段(data/invocation/order)。 */
    private String firstText(JsonNode root, String... keys) {
        if (root == null) return null;
        String v = firstTextShallow(root, keys);
        if (v != null) return v;
        for (String env : new String[]{"data", "invocation", "order"}) {
            JsonNode sub = root.get(env);
            if (sub != null && sub.isObject()) {
                v = firstTextShallow(sub, keys);
                if (v != null) return v;
            }
        }
        return null;
    }

    private String firstTextShallow(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode n = node.get(k);
            if (n != null && !n.isNull()) {
                if (n.isTextual()) return n.asText();
                if (n.isNumber()) return n.asText();
            }
        }
        return null;
    }

    /** 在顶层及常见信封字段(data/invocation/order)中查找指定对象键。 */
    private JsonNode findNode(JsonNode root, String key) {
        if (root == null) return null;
        if (root.has(key)) return root.get(key);
        for (String env : new String[]{"data", "invocation", "order"}) {
            JsonNode sub = root.get(env);
            if (sub != null && sub.isObject() && sub.has(key)) return sub.get(key);
        }
        return null;
    }

    private String ellipsis(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300);
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (insecure) {
            try {
                TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) { }
                    public void checkServerTrusted(X509Certificate[] c, String a) { }
                }};
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, trustAll, new SecureRandom());
                b.sslContext(ctx);
            } catch (Exception e) {
                log.warn("[Hub] 忽略证书配置失败，回落到默认证书校验: {}", e.getMessage());
            }
        }
        return b.build();
    }
}
