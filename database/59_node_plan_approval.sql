-- 59: 节点计划审批流(工程师提交计划 -> 经理审批) + 企业微信通知配置
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_approval_status VARCHAR(30);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_submitted_by VARCHAR(64);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_submitted_at TIMESTAMPTZ;
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_approved_by VARCHAR(64);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_approved_at TIMESTAMPTZ;
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_reject_note TEXT;

-- 企业微信通知配置(群机器人 webhook)；留空则不发
CREATE TABLE IF NOT EXISTS plm_notify_config (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL UNIQUE,
    webhook_url TEXT,
    enabled SMALLINT DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT now()
);
INSERT INTO plm_notify_config(channel, webhook_url, enabled)
SELECT 'wecom', '', 0 WHERE NOT EXISTS (SELECT 1 FROM plm_notify_config WHERE channel='wecom');
