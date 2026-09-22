-- =============================================================================
-- HJ-PLM V1.1 增量 19: DQ 自动智能修复
-- 说明:
--   ① plm_dq_rule 新增 auto_fix_strategy JSONB 字段
--      每条规则可挂自动修复策略: { "strategy":"FILL_DEFAULT|FILL_FROM_PARAM|TRIM_WS|MARK_OBSOLETE|LINK_TO_ISSUE|NO_OP",
--                                     "field":"unit", "defaultValue":"PCS",
--                                     "paramKey":"ip_rating", "template":"LUMINAIRE_FG" }
--   ② 新增 plm_dq_fix_attempt 表
--      记录每次自动修复尝试: 原始债务 → 修复动作 → 修复前快照 → 修复后验证 → 状态(已解决/部分/失败/需人工)
--      通过 source_debt_id 关联到原始问题(plm_dq_debt.id),确认解决后回写 debt.closed_at + waived_by='AUTO_FIX'
--   ③ 默认策略示例:
--      PART_UNIT_WARN  → FILL_DEFAULT(unit='PCS')
--      PART_VERSION_DEFAULT → FILL_DEFAULT(versionNo='V1.0')
--      PART_NO_FORMAT → NO_OP (需人工改名)
--      PARAM_REQUIRED_*  → FILL_FROM_PARAM(templateKey='LUMINAIRE_FG', fallback='TO_ISSUE')
--      BOM_NO_CYCLE    → NO_OP (需业务手工)
--      ECN_IMPACT_REQUIRED → LINK_TO_ISSUE (升级为正式改善单)
-- =============================================================================

ALTER TABLE plm_dq_rule ADD COLUMN IF NOT EXISTS auto_fix_strategy JSONB;

CREATE TABLE IF NOT EXISTS plm_dq_fix_attempt (
    id              BIGSERIAL PRIMARY KEY,
    source_debt_id  BIGINT NOT NULL,                  -- 关联 plm_dq_debt.id,确认闭环追溯
    rule_code       VARCHAR(64) NOT NULL,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    strategy        VARCHAR(32) NOT NULL,             -- FILL_DEFAULT / TRIM_WS / MARK_OBSOLETE / LINK_TO_ISSUE / NO_OP
    before_state    JSONB,                            -- 修复前关键字段快照
    after_state     JSONB,                            -- 修复后字段值
    before_score    NUMERIC(5,2),                     -- 修复前 DQ 分
    after_score     NUMERIC(5,2),                     -- 修复后再跑 DQ 分
    resolved        BOOLEAN NOT NULL DEFAULT FALSE,   -- 是否真正解决(after_score>before_score 且该规则PASS)
    linked_issue_id BIGINT,                           -- 升级到正式改善单的 ID(LINK_TO_ISSUE 策略时填)
    error_msg       VARCHAR(2048),
    attempted_by    VARCHAR(64) NOT NULL DEFAULT 'AUTO',  -- AUTO(夜检)/USER(手动)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ                       -- 回检确认时间
);
CREATE INDEX IF NOT EXISTS idx_dq_fix_attempt_debt ON plm_dq_fix_attempt(source_debt_id);
CREATE INDEX IF NOT EXISTS idx_dq_fix_attempt_rule ON plm_dq_fix_attempt(rule_code, resolved);
CREATE INDEX IF NOT EXISTS idx_dq_fix_attempt_issue ON plm_dq_fix_attempt(linked_issue_id);

COMMENT ON TABLE plm_dq_fix_attempt IS 'DQ 自动修复尝试记录(修复→回检→闭环)';

-- 默认修复策略(每条规则一行,ON CONFLICT 更新 strategy 字段)
UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"FILL_DEFAULT","field":"unit","defaultValue":"PCS"}'::jsonb
WHERE rule_code = 'PART_UNIT_WARN' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"FILL_DEFAULT","field":"versionNo","defaultValue":"V1.0"}'::jsonb
WHERE rule_code = 'PART_VERSION_DEFAULT' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"FILL_DEFAULT","field":"specification","defaultValue":"N/A"}'::jsonb
WHERE rule_code = 'PART_SPECIFICATION' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"NO_OP","reason":"料号格式需业务人工确认"}'::jsonb
WHERE rule_code = 'PART_NO_FORMAT' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"NO_OP","reason":"成品IP防护等级需业务确认,自动填充可能误判"}'::jsonb
WHERE rule_code = 'PART_FG_IP' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"NO_OP","reason":"参数模板项需业务人工填写"}'::jsonb
WHERE rule_code LIKE 'PARAM_REQUIRED_%' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"LINK_TO_ISSUE","severity":"MEDIUM","title":"ECN缺失影响面"}'::jsonb
WHERE rule_code = 'ECN_IMPACT_REQUIRED' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"LINK_TO_ISSUE","severity":"MEDIUM","title":"BOM循环引用/结构异常"}'::jsonb
WHERE rule_code = 'BOM_NO_CYCLE' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"LINK_TO_ISSUE","severity":"HIGH","title":"BOM子件主数据缺失"}'::jsonb
WHERE rule_code = 'BOM_CHILD_EXISTS' AND auto_fix_strategy IS NULL;

UPDATE plm_dq_rule SET auto_fix_strategy =
    '{"strategy":"FILL_DEFAULT","field":"status","defaultValue":"IN_PRODUCTION"}'::jsonb
WHERE rule_code = 'BOM_ITEM_UNIT' AND auto_fix_strategy IS NULL;