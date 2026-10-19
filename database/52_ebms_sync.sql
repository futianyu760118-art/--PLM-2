-- =============================================================================
-- HJ-PLM 增量 52: PLM → EBMS 即时同步(事件推送 webhook 订阅)
-- 单向、全量、事件推送。事件由 OutboxPublisherJob 轮询推送。
-- target_url 请按 EBMS 实际部署地址调整。
-- =============================================================================

INSERT INTO plm_webhook_subscription (name, target_url, secret, event_types, enabled, created_at)
SELECT 'EBMS', 'http://192.168.0.71:5000/api/sync/events', '', '*', true, now()
WHERE NOT EXISTS (SELECT 1 FROM plm_webhook_subscription WHERE name='EBMS');
