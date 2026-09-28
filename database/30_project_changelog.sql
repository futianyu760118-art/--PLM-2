-- =============================================================================
-- HJ-PLM 增量 30: 项目变更列表 (变更留痕)
-- 项目内数据(节点/工作表)变更自动记录; 工作表变更自动同步引用到节点/跟踪
-- =============================================================================

CREATE TABLE IF NOT EXISTS plm_project_change_log (
    id BIGSERIAL PRIMARY KEY,
    project_id   BIGINT,
    project_no   VARCHAR(64),
    object_type  VARCHAR(32),      -- PROJECT_NODE / SPEC / CONFIG / REVIEW / TRIAL / TEST / SAMPLE / SHIPMENT / PLAN
    object_id    VARCHAR(64),
    node_code    VARCHAR(32),
    action       VARCHAR(16),      -- CREATE / UPDATE / DELETE / ROLLUP / CELL
    field_name   VARCHAR(64),
    old_value    TEXT,
    new_value    TEXT,
    source       VARCHAR(16) DEFAULT 'UI',  -- UI / ROLLUP / SYNC / IMPORT
    operator     VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_change_log_proj ON plm_project_change_log(project_no);
CREATE INDEX IF NOT EXISTS idx_change_log_time ON plm_project_change_log(created_at DESC);
COMMENT ON TABLE plm_project_change_log IS '项目数据变更列表(留痕)';

-- =============================================================================
-- Done.
-- =============================================================================
