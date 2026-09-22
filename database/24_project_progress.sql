-- =============================================================================
-- HJ-PLM 增量 24: 研发项目 - 进度跟踪(小模型/试点)
-- 19 个进度节点(每项目一行一节点) + 节点证据文件
-- 完成硬标准: status=DONE 且 actual_date 非空 且 证据文件>=1
-- =============================================================================

CREATE TABLE IF NOT EXISTS plm_project_node (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    project_no      VARCHAR(64),
    node_code       VARCHAR(32) NOT NULL,
    seq             INT NOT NULL,
    node_name       VARCHAR(64) NOT NULL,
    is_key          SMALLINT NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'NOT_SET',
    plan_date       DATE,
    actual_date     DATE,
    owner           VARCHAR(64),
    delivery_desc   VARCHAR(512),
    remark          VARCHAR(1024),
    evidence_count  INT NOT NULL DEFAULT 0,
    edit_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, node_code)
);
CREATE INDEX IF NOT EXISTS idx_project_node_proj ON plm_project_node(project_id, seq);
CREATE INDEX IF NOT EXISTS idx_project_node_status ON plm_project_node(status);
COMMENT ON TABLE plm_project_node IS '研发项目进度节点(19节点/项目)';

CREATE TABLE IF NOT EXISTS plm_project_node_evidence (
    id           BIGSERIAL PRIMARY KEY,
    node_id      BIGINT NOT NULL REFERENCES plm_project_node(id) ON DELETE CASCADE,
    project_id   BIGINT,
    node_code    VARCHAR(32),
    file_id      BIGINT,
    file_name    VARCHAR(256),
    doc_type     VARCHAR(32),
    note         VARCHAR(512),
    uploaded_by  VARCHAR(64),
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_node_evidence_node ON plm_project_node_evidence(node_id);
COMMENT ON TABLE plm_project_node_evidence IS '进度节点证据文件';
