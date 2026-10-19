package com.hjgd.plm.event.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * PLM → EBMS 即时同步订阅注册器。
 * 启动时按配置 target_url 注册/更新 EBMS webhook 订阅(幂等)。
 * 配置: plm.ebms.webhook-url (环境变量 PLM_EBMS_WEBHOOK_URL)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EbmsWebhookRegistrar {

    private final JdbcTemplate jdbcTemplate;

    @Value("${plm.ebms.webhook-url:http://192.168.0.71:5000/api/sync/events}")
    private String ebmsWebhookUrl;

    @PostConstruct
    public void register() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM plm_webhook_subscription WHERE name='EBMS'", Integer.class);
            if (cnt != null && cnt > 0) {
                jdbcTemplate.update(
                        "UPDATE plm_webhook_subscription SET target_url=?, enabled=true WHERE name='EBMS'",
                        ebmsWebhookUrl);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO plm_webhook_subscription (name, target_url, secret, event_types, enabled, created_at) " +
                        "VALUES ('EBMS', ?, '', '*', true, now())",
                        ebmsWebhookUrl);
            }
            log.info("[EBMS同步] webhook 订阅已注册: {}", ebmsWebhookUrl);
        } catch (Exception e) {
            log.warn("[EBMS同步] 订阅注册跳过(表未就绪): {}", e.getMessage());
        }
    }
}
