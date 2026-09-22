-- Waves 1-6 增量：BOM三态 / ECN影响面 / 导出任务 / 度量Job支撑

-- ===== Step2: BOM 三态 =====
ALTER TABLE plm_bom ADD COLUMN IF NOT EXISTS bom_type VARCHAR(16) DEFAULT 'EBOM';
UPDATE plm_bom SET bom_type = 'EBOM' WHERE bom_type IS NULL;
COMMENT ON COLUMN plm_bom.bom_type IS 'EBOM/MBOM/SBOM';

ALTER TABLE plm_bom_item ADD COLUMN IF NOT EXISTS process_op VARCHAR(64);
ALTER TABLE plm_bom_item ADD COLUMN IF NOT EXISTS sbom_class VARCHAR(8);
COMMENT ON COLUMN plm_bom_item.process_op IS 'MBOM工序';
COMMENT ON COLUMN plm_bom_item.sbom_class IS 'SBOM分级 A/B/C';

-- ===== Step3: ECN 影响面 =====
CREATE TABLE IF NOT EXISTS plm_ecn_impact (
    id              BIGSERIAL PRIMARY KEY,
    ecn_id          BIGINT NOT NULL,
    ecn_no          VARCHAR(64) NOT NULL,
    impact_type     VARCHAR(32) NOT NULL,
    target_type     VARCHAR(32) NOT NULL,
    target_id       VARCHAR(64),
    target_ref      VARCHAR(128),
    action_code     VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    handled_at      TIMESTAMPTZ,
    result_json     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ecn_impact_ecn ON plm_ecn_impact(ecn_id);
CREATE INDEX IF NOT EXISTS idx_ecn_impact_status ON plm_ecn_impact(status);

-- ===== Step5: 导出任务 =====
CREATE TABLE IF NOT EXISTS plm_export_task (
    id              BIGSERIAL PRIMARY KEY,
    export_no       VARCHAR(64) NOT NULL UNIQUE,
    export_type     VARCHAR(32) NOT NULL,
    format          VARCHAR(8) NOT NULL DEFAULT 'csv',
    name            VARCHAR(128),
    spec_json       TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    file_path       VARCHAR(512),
    row_count       INT,
    error_msg       VARCHAR(1024),
    requested_by    VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_export_status ON plm_export_task(status, created_at);

-- ===== Step6: 改善成效已有(plm_improve_result in 10) =====
-- 度量值表已存在(plm_metric_value in 10)
