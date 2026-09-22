-- Step1: Project NPI 模块 + 阶段门

CREATE TABLE IF NOT EXISTS plm_project (
    id              BIGSERIAL PRIMARY KEY,
    project_no      VARCHAR(64) NOT NULL UNIQUE,
    project_name    VARCHAR(256) NOT NULL,
    part_no         VARCHAR(64),
    customer_code   VARCHAR(64),
    customer_name   VARCHAR(200),
    project_type    VARCHAR(32) DEFAULT 'NEW',
    project_level   VARCHAR(8) DEFAULT 'C',
    urgency         VARCHAR(32) DEFAULT 'normal',
    owner           VARCHAR(64),
    department      VARCHAR(64),
    start_date      DATE,
    target_date     DATE,
    close_date      DATE,
    current_gate    VARCHAR(16) DEFAULT 'G0',
    gate_status     VARCHAR(16) DEFAULT 'ON_TRACK',
    project_amount  NUMERIC(14,2) DEFAULT 0,
    order_amount    NUMERIC(14,2) DEFAULT 0,
    invest_amount   NUMERIC(14,2) DEFAULT 0,
    annual_order    VARCHAR(32),
    market_date     DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    risk_level      VARCHAR(8) DEFAULT 'green',
    remarks         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_project_part ON plm_project(part_no);
CREATE INDEX IF NOT EXISTS idx_project_status ON plm_project(status);
CREATE INDEX IF NOT EXISTS idx_project_owner ON plm_project(owner);

CREATE TABLE IF NOT EXISTS plm_project_gate_log (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES plm_project(id) ON DELETE CASCADE,
    gate_code       VARCHAR(16) NOT NULL,
    gate_name       VARCHAR(64),
    result          VARCHAR(16) NOT NULL,
    planned_date    DATE,
    actual_date     DATE,
    operator        VARCHAR(64),
    comment         TEXT,
    evidence_ref    VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gate_log_project ON plm_project_gate_log(project_id, gate_code);

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES ('PROJECT_NO', 'HJ', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;
