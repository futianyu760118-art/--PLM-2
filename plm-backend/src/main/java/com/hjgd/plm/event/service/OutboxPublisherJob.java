package com.hjgd.plm.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${plm.webhook.enabled:false}")
    private boolean webhookEnabled;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 30000)
    public void publish() {
        List<Map<String, Object>> events;
        try {
            events = jdbcTemplate.queryForList(
                    "SELECT id, event_id, event_type, aggregate_type, aggregate_id, payload_json, retry_count " +
                    "FROM plm_domain_event WHERE status='NEW' ORDER BY id ASC LIMIT 50");
        } catch (Exception e) {
            return;
        }
        if (events.isEmpty()) return;

        log.debug("[Outbox] processing {} events", events.size());

        List<Map<String, Object>> subs = loadSubscriptions();
        for (Map<String, Object> event : events) {
            Long eventId = ((Number) event.get("id")).longValue();
            String eventType = String.valueOf(event.get("event_type"));
            int retryCount = event.get("retry_count") == null ? 0 : ((Number) event.get("retry_count")).intValue();

            if (retryCount >= MAX_RETRIES) {
                markFailed(eventId, "超过最大重试次数 " + MAX_RETRIES);
                continue;
            }

            incrementRetry(eventId);

            boolean allDelivered = true;

            if (webhookEnabled && !subs.isEmpty()) {
                for (Map<String, Object> sub : subs) {
                    if (!matchesEvent(String.valueOf(sub.get("event_types")), eventType)) continue;
                    boolean ok = deliver(event, sub);
                    if (!ok) allDelivered = false;
                }
            } else {
                allDelivered = true;
            }

            if (allDelivered) {
                markPublished(eventId);
            }
        }
    }

    private List<Map<String, Object>> loadSubscriptions() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT name, target_url, secret, event_types FROM plm_webhook_subscription WHERE enabled=true");
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean matchesEvent(String eventTypesCsv, String eventType) {
        if (eventTypesCsv == null) return false;
        for (String t : eventTypesCsv.split(",")) {
            if (t.trim().equals("*") || t.trim().equals(eventType)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean deliver(Map<String, Object> event, Map<String, Object> sub) {
        String url = String.valueOf(sub.get("target_url"));
        String secret = sub.get("secret") == null ? null : String.valueOf(sub.get("secret"));

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", event.get("event_id"));
            payload.put("eventType", event.get("event_type"));
            payload.put("aggregateType", event.get("aggregate_type"));
            payload.put("aggregateId", event.get("aggregate_id"));
            payload.put("occurredAt", java.time.Instant.now().toString());

            String payloadJson = String.valueOf(event.get("payload_json"));
            if (payloadJson != null && !payloadJson.isEmpty() && !"null".equals(payloadJson)) {
                try {
                    payload.put("data", objectMapper.readValue(payloadJson, Map.class));
                } catch (Exception e) {
                    payload.put("data", payloadJson);
                }
            }

            String bodyJson = objectMapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (secret != null && !secret.isBlank()) {
                String sig = hmacSha256(bodyJson, secret);
                headers.set("X-Webhook-Signature", sig);
            }

            HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[Webhook] delivered {} -> {} ({})", event.get("event_type"), url, resp.getStatusCode());
                return true;
            } else {
                log.warn("[Webhook] {} -> {} returned {}", event.get("event_type"), url, resp.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.warn("[Webhook] deliver failed {} -> {}: {}", event.get("event_type"), url, e.getMessage());
            return false;
        }
    }

    private void markPublished(Long eventId) {
        try {
            jdbcTemplate.update(
                    "UPDATE plm_domain_event SET status='PUBLISHED', published_at=NOW() WHERE id=?", eventId);
        } catch (Exception e) {
            log.debug("mark published failed: {}", e.getMessage());
        }
    }

    private void markFailed(Long eventId, String reason) {
        try {
            jdbcTemplate.update(
                    "UPDATE plm_domain_event SET status='FAILED', published_at=NOW() WHERE id=?", eventId);
            log.warn("[Outbox] event {} marked FAILED: {}", eventId, reason);
        } catch (Exception e) {
            log.debug("mark failed error: {}", e.getMessage());
        }
    }

    private void incrementRetry(Long eventId) {
        try {
            jdbcTemplate.update(
                    "UPDATE plm_domain_event SET retry_count = retry_count + 1 WHERE id=?", eventId);
        } catch (Exception ignored) {
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
