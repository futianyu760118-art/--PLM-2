-- 60: 企业微信自建应用(按人推送应用消息)配置 + 用户企业微信userid
ALTER TABLE plm_notify_config ADD COLUMN IF NOT EXISTS corp_id VARCHAR(64);
ALTER TABLE plm_notify_config ADD COLUMN IF NOT EXISTS agent_id VARCHAR(32);
ALTER TABLE plm_notify_config ADD COLUMN IF NOT EXISTS corp_secret VARCHAR(128);
INSERT INTO plm_notify_config(channel, enabled)
SELECT 'wecom_app', 0 WHERE NOT EXISTS (SELECT 1 FROM plm_notify_config WHERE channel='wecom_app');

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wecom_userid VARCHAR(64);
