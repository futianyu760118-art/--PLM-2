-- Wave0: 统一生命周期 / 零件扩展字段 / 版本快照可用
-- 废除双状态并行：status 为唯一 lifecycle_state；lifecycle_status 仅兼容同步

ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS phase VARCHAR(32) DEFAULT 'CONCEPT';
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS part_category VARCHAR(32) DEFAULT 'COMPONENT';
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS name_en VARCHAR(200);
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS product_type VARCHAR(64);
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS ip_rating VARCHAR(32);
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS power_w VARCHAR(32);

COMMENT ON COLUMN plm_material.status IS 'V1.1 唯一生命周期状态 lifecycle_state';
COMMENT ON COLUMN plm_material.lifecycle_status IS 'DEPRECATED: 与 status 同步，勿单独写入';
COMMENT ON COLUMN plm_material.phase IS 'NPI阶段 CONCEPT/STRUCTURE/MOLD_DEV/TRIAL/MASS_PRODUCTION/EOL';
COMMENT ON COLUMN plm_material.part_category IS 'PRODUCT/ASSEMBLY/COMPONENT/STANDARD/PACKAGING';

-- 仅同步 status 值为合法 archive_status_enum 标签的行(IN_PRODUCTION 不在 archive 枚举中,跳过)
UPDATE plm_material SET lifecycle_status = status::text::archive_status_enum
WHERE status::text IN ('DRAFT','REVIEWING','RELEASED','CHANGING','OBSOLETE','SEALED')
  AND lifecycle_status::text IS DISTINCT FROM status::text;

UPDATE plm_material SET part_category = 'PRODUCT'
WHERE material_type = 'FINISHED' AND (part_category IS NULL OR part_category = 'COMPONENT');

UPDATE plm_material SET phase = 'MASS_PRODUCTION'
WHERE status IN ('IN_PRODUCTION') AND (phase IS NULL OR phase = 'CONCEPT');

UPDATE plm_material SET phase = 'STRUCTURE'
WHERE status IN ('DRAFT', 'REVIEWING') AND phase = 'CONCEPT';

-- 生命周期转换配置（可扩展）
CREATE TABLE IF NOT EXISTS plm_lifecycle_transition (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    from_state      VARCHAR(32) NOT NULL,
    to_state        VARCHAR(32) NOT NULL,
    action_code     VARCHAR(64) NOT NULL,
    roles           VARCHAR(256),
    require_dq      BOOLEAN NOT NULL DEFAULT TRUE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (object_type, from_state, action_code)
);

INSERT INTO plm_lifecycle_transition (object_type, from_state, to_state, action_code, roles, require_dq) VALUES
('PART', 'DRAFT', 'REVIEWING', 'submit_review', 'ENGINEER,ADMIN,RD_LEAD', true),
('PART', 'DRAFT', 'RELEASED', 'release', 'RD_LEAD,ADMIN', true),
('PART', 'REVIEWING', 'RELEASED', 'release', 'RD_LEAD,ADMIN', true),
('PART', 'REVIEWING', 'DRAFT', 'reject_review', 'RD_LEAD,ADMIN', false),
('PART', 'RELEASED', 'CHANGING', 'start_change', 'SYSTEM,ENGINEER,ADMIN', false),
('PART', 'IN_PRODUCTION', 'CHANGING', 'start_change', 'SYSTEM,ENGINEER,ADMIN', false),
('PART', 'CHANGING', 'RELEASED', 'finish_change', 'SYSTEM,ADMIN', true),
('PART', 'CHANGING', 'IN_PRODUCTION', 'finish_change_mp', 'SYSTEM,ADMIN', true),
('PART', 'RELEASED', 'IN_PRODUCTION', 'to_production', 'RD_LEAD,ADMIN,SCM_LEAD', true),
('PART', 'RELEASED', 'OBSOLETE', 'obsolete', 'RD_LEAD,ADMIN', true),
('PART', 'IN_PRODUCTION', 'OBSOLETE', 'obsolete', 'RD_LEAD,ADMIN', true),
('PART', 'OBSOLETE', 'SEALED', 'seal', 'ADMIN', false)
ON CONFLICT (object_type, from_state, action_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS plm_lifecycle_history (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    from_state      VARCHAR(32),
    to_state        VARCHAR(32) NOT NULL,
    action_code     VARCHAR(64) NOT NULL,
    operator        VARCHAR(64),
    comment         VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_lc_hist_obj ON plm_lifecycle_history(object_type, object_id);

-- 领域事件（若 10 未执行）
CREATE TABLE IF NOT EXISTS plm_domain_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(32),
    aggregate_id    VARCHAR(64),
    payload_json    TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_event_id ON plm_domain_event(event_id);
CREATE INDEX IF NOT EXISTS idx_domain_event_status ON plm_domain_event(status, created_at);

-- 与 10_v1_1_lean_agent_kpi.sql 保持同一口径(10 先执行, 这里是空操作)
CREATE TABLE IF NOT EXISTS plm_code_issue_log (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64),
    generated_code  VARCHAR(128) NOT NULL,
    rule_code       VARCHAR(64),
    rule_id         BIGINT,
    rule_version    VARCHAR(16),
    context_json    JSONB,
    issuer_id       BIGINT,
    source          VARCHAR(16) NOT NULL DEFAULT 'UI',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_dq_run (
    id              BIGSERIAL PRIMARY KEY,
    batch_no        VARCHAR(64),
    trigger_type    VARCHAR(16) NOT NULL,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    rule_code       VARCHAR(64),
    severity        VARCHAR(8),
    result          VARCHAR(8) NOT NULL,
    message         VARCHAR(1024),
    detail_json     JSONB,
    duration_ms     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dq_run_obj ON plm_dq_run(object_type, object_id, created_at DESC);

CREATE TABLE IF NOT EXISTS plm_dq_object_score (
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    score_0_100     NUMERIC(5,2) NOT NULL DEFAULT 100,
    block_count     INT NOT NULL DEFAULT 0,
    warn_count      INT NOT NULL DEFAULT 0,
    info_count      INT NOT NULL DEFAULT 0,
    last_run_at     TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (object_type, object_id)
);

CREATE TABLE IF NOT EXISTS plm_work_item (
    id              BIGSERIAL PRIMARY KEY,
    item_no         VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    ref_type        VARCHAR(32),
    ref_id          VARCHAR(64),
    priority        INT NOT NULL DEFAULT 3,
    owner_id        BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    sla_due_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES
('ISSUE_NO', 'IQ', 'yyyyMMdd', 4, 0),
('WORK_ITEM', 'WI', 'yyyyMMdd', 4, 0),
('EXPORT_NO', 'EX', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;
