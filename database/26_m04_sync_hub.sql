-- =============================================================================
-- HJ-PLM 增量 26: M04 研发自治中心 - 同步中枢 (Sync Hub)
-- 依据: docs/m04-rd-autonomous-migration-sync-plan.md (P0/P1)
--   * Sync Hub: peer / object / inbox / conflict / checkpoint
--   * 幂等与溯源列: correlation_id / source_system / revision / sync_status
--   * P1 项目域新增表: 复盘 / 供应链异常 / 销售推广
--   * D1: 进度节点 19 -> 22 (补 APPEARANCE/STRUCTURE/ELECTRONICS) + 存量回填
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 对端系统注册
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_sync_peer (
    id            BIGSERIAL PRIMARY KEY,
    system_code   VARCHAR(32) NOT NULL,          -- EBMS / PLM2
    system_name   VARCHAR(128),
    base_url      VARCHAR(256),
    auth_type     VARCHAR(16) DEFAULT 'HMAC',    -- HMAC / JWT
    auth_secret   VARCHAR(256),
    direction     VARCHAR(8)  DEFAULT 'BIDIR',   -- BIDIR / IN / OUT
    enabled       SMALLINT    NOT NULL DEFAULT 1,
    heartbeat_at  TIMESTAMPTZ,
    last_cursor   VARCHAR(128),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (system_code)
);
COMMENT ON TABLE plm_sync_peer IS '同步对端系统注册';

-- ---------------------------------------------------------------------------
-- 2. 对象映射 (跨系统同一对象的身份对应)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_sync_object (
    id             BIGSERIAL PRIMARY KEY,
    object_type    VARCHAR(32) NOT NULL,          -- PRODUCT/PROJECT/PROJECT_NODE/BOM/...
    external_key   VARCHAR(256) NOT NULL,         -- part_no / project_no:node_code / doc_no
    source_system  VARCHAR(16),                   -- 最近写入来源
    source_id      VARCHAR(64),
    target_system  VARCHAR(16),
    target_id      VARCHAR(64),
    revision       INT NOT NULL DEFAULT 0,
    checksum       VARCHAR(64),                   -- 内容 md5
    last_synced_at TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (object_type, external_key)
);
CREATE INDEX IF NOT EXISTS idx_sync_object_ext ON plm_sync_object(external_key);
COMMENT ON TABLE plm_sync_object IS '跨系统对象映射与版本';

-- ---------------------------------------------------------------------------
-- 3. 入站队列 (EBMS -> PLM-2)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_sync_inbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    correlation_id  UUID,
    object_type     VARCHAR(32) NOT NULL,
    external_key    VARCHAR(256),
    operation       VARCHAR(16),                  -- CREATE/UPDATE/DELETE
    source_system   VARCHAR(16),
    source_revision INT,
    payload_json    JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW', -- NEW/APPLIED/CONFLICT/FAILED
    error           VARCHAR(1024),
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at      TIMESTAMPTZ,
    UNIQUE (event_id)
);
CREATE INDEX IF NOT EXISTS idx_sync_inbox_status ON plm_sync_inbox(status);
CREATE INDEX IF NOT EXISTS idx_sync_inbox_ext ON plm_sync_inbox(object_type, external_key);
COMMENT ON TABLE plm_sync_inbox IS '同步入站队列(EBMS->PLM-2)';

-- ---------------------------------------------------------------------------
-- 4. 冲突记录 (双主可写)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_sync_conflict (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    external_key    VARCHAR(256),
    field_name      VARCHAR(64),
    local_value     TEXT,
    remote_value    TEXT,
    local_ts        TIMESTAMPTZ,
    remote_ts       TIMESTAMPTZ,
    local_revision  INT,
    remote_revision INT,
    resolution      VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/LOCAL/REMOTE/MERGED
    resolved_by     VARCHAR(64),
    resolved_at     TIMESTAMPTZ,
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sync_conflict_status ON plm_sync_conflict(resolution);
CREATE INDEX IF NOT EXISTS idx_sync_conflict_ext ON plm_sync_conflict(object_type, external_key);
COMMENT ON TABLE plm_sync_conflict IS '双向同步冲突记录(人工裁决)';

-- ---------------------------------------------------------------------------
-- 5. 对账水位
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_sync_checkpoint (
    id          BIGSERIAL PRIMARY KEY,
    system_code VARCHAR(32) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    cursor      VARCHAR(128),
    last_run_at TIMESTAMPTZ,
    lag_count   INT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (system_code, object_type)
);
COMMENT ON TABLE plm_sync_checkpoint IS '同步对账水位';

-- ---------------------------------------------------------------------------
-- 6. 出站复用 plm_domain_event, 补充同步列
-- ---------------------------------------------------------------------------
ALTER TABLE plm_domain_event ADD COLUMN IF NOT EXISTS correlation_id UUID;
ALTER TABLE plm_domain_event ADD COLUMN IF NOT EXISTS source_system VARCHAR(16);
ALTER TABLE plm_domain_event ADD COLUMN IF NOT EXISTS external_key VARCHAR(256);
ALTER TABLE plm_domain_event ADD COLUMN IF NOT EXISTS revision INT;

-- ---------------------------------------------------------------------------
-- 7. 通用同步列 (关键 M04 表)
-- ---------------------------------------------------------------------------
ALTER TABLE plm_project          ADD COLUMN IF NOT EXISTS source_system VARCHAR(16);
ALTER TABLE plm_project          ADD COLUMN IF NOT EXISTS revision INT DEFAULT 0;
ALTER TABLE plm_project          ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) DEFAULT 'SYNCED';
ALTER TABLE plm_project_node     ADD COLUMN IF NOT EXISTS source_system VARCHAR(16);
ALTER TABLE plm_project_node     ADD COLUMN IF NOT EXISTS revision INT DEFAULT 0;
ALTER TABLE plm_project_node     ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) DEFAULT 'SYNCED';
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS source_system VARCHAR(16);
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS revision INT DEFAULT 0;
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) DEFAULT 'SYNCED';
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS internal_model VARCHAR(128);
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS price_rmb NUMERIC(18,4);
ALTER TABLE plm_material         ADD COLUMN IF NOT EXISTS price_usd NUMERIC(18,4);
ALTER TABLE plm_bom              ADD COLUMN IF NOT EXISTS source_system VARCHAR(16);
ALTER TABLE plm_bom              ADD COLUMN IF NOT EXISTS revision INT DEFAULT 0;
ALTER TABLE plm_bom              ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) DEFAULT 'SYNCED';

-- ---------------------------------------------------------------------------
-- 8. P1 项目域新增表 (研发相关; 映射见 docs/m04-data-mapping-v1.md)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plm_project_review (
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT,
    project_no        VARCHAR(64),
    project_name      VARCHAR(256),
    goal_original     TEXT,
    goal_milestone    TEXT,
    result_highlights TEXT,
    result_lowlights  TEXT,
    result_actual     TEXT,
    success_factors   TEXT,
    failure_causes    TEXT,
    insights          TEXT,
    experience        TEXT,
    action_plan       TEXT,
    remarks           TEXT,
    source_system     VARCHAR(16),
    revision          INT DEFAULT 0,
    sync_status       VARCHAR(16) DEFAULT 'SYNCED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_proj_review_proj ON plm_project_review(project_id);
COMMENT ON TABLE plm_project_review IS '研发项目复盘(经验库)';

CREATE TABLE IF NOT EXISTS plm_project_supply_issue (
    id                  BIGSERIAL PRIMARY KEY,
    occur_date          DATE,
    proposer            VARCHAR(64),
    product_name        VARCHAR(256),
    order_no            VARCHAR(64),
    project_no          VARCHAR(64),
    problem_desc        TEXT,
    temp_measure        TEXT,
    cause_analysis      TEXT,
    long_term_measure   TEXT,
    long_term_date      DATE,
    responsible_person  VARCHAR(64),
    responsible_dept    VARCHAR(64),
    plan_complete_date  DATE,
    audit               VARCHAR(128),
    closed              SMALLINT NOT NULL DEFAULT 0,
    remarks             TEXT,
    source_system       VARCHAR(16),
    revision            INT DEFAULT 0,
    sync_status         VARCHAR(16) DEFAULT 'SYNCED',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_proj_supply_proj ON plm_project_supply_issue(project_no);
COMMENT ON TABLE plm_project_supply_issue IS '研发项目供应链品质异常';

CREATE TABLE IF NOT EXISTS plm_project_sales_promotion (
    id               BIGSERIAL PRIMARY KEY,
    project_no       VARCHAR(64),
    product_model    VARCHAR(128),
    salesperson      VARCHAR(64),
    customer         VARCHAR(256),
    appearance       TEXT,
    price            TEXT,
    performance      TEXT,
    function_feedback TEXT,
    progress         VARCHAR(256),
    remarks          TEXT,
    source_system    VARCHAR(16),
    revision         INT DEFAULT 0,
    sync_status      VARCHAR(16) DEFAULT 'SYNCED',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_proj_sales_proj ON plm_project_sales_promotion(project_no);
COMMENT ON TABLE plm_project_sales_promotion IS '研发项目销售推广进度';

-- ---------------------------------------------------------------------------
-- 9. D1: 进度节点 19 -> 22; seq 重排 + 存量项目回填补 3 节点
--    新序: ...HAND_SAMPLE 7, APPEARANCE 8, STRUCTURE 9, ELECTRONICS 10,
--          MOLD 11, MOLD_SAMPLE 12, PACKAGING 13, ELEC_TRIAL 14, RD_TRIAL 15,
--          ENG_TRIAL 16, PROD_TRIAL 17, TEST_REPORT 18, TECH_TRANSFER 19,
--          SHIPMENT 20, REVIEW 21, OTHER 22
-- ---------------------------------------------------------------------------
UPDATE plm_project_node n
SET seq = m.new_seq
FROM (VALUES
    ('PLAN',1),('BOM',2),('SPEC',3),('CONFIG',4),('MOLD_DRAWING',5),
    ('MOLD_REVIEW',6),('HAND_SAMPLE',7),('APPEARANCE',8),('STRUCTURE',9),
    ('ELECTRONICS',10),('MOLD',11),('MOLD_SAMPLE',12),('PACKAGING',13),
    ('ELEC_TRIAL',14),('RD_TRIAL',15),('ENG_TRIAL',16),('PROD_TRIAL',17),
    ('TEST_REPORT',18),('TECH_TRANSFER',19),('SHIPMENT',20),('REVIEW',21),
    ('OTHER',22)
) AS m(code, new_seq)
WHERE n.node_code = m.code;

INSERT INTO plm_project_node (project_id, project_no, node_code, seq, node_name, is_key, status)
SELECT p.id, p.project_no, v.node_code, v.seq, v.node_name, v.is_key, 'NOT_SET'
FROM plm_project p
CROSS JOIN (VALUES
    ('APPEARANCE', 8,  '外观', 0),
    ('STRUCTURE',  9,  '结构', 0),
    ('ELECTRONICS',10, '电子', 0)
) AS v(node_code, seq, node_name, is_key)
WHERE NOT EXISTS (
    SELECT 1 FROM plm_project_node n
    WHERE n.project_id = p.id AND n.node_code = v.node_code
);

-- ---------------------------------------------------------------------------
-- 10. 初始化对端与水位 (占位, 可按部署调整)
-- ---------------------------------------------------------------------------
INSERT INTO plm_sync_peer (system_code, system_name, direction, enabled)
VALUES ('EBMS', 'EBMS 企业经营管理系统', 'BIDIR', 1)
ON CONFLICT (system_code) DO NOTHING;

-- =============================================================================
-- Done.
-- =============================================================================
