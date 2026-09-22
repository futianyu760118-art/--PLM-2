-- =============================================================================
-- HJ-PLM SME V1.1 增量 DDL：自动编号增强 / 数据自检 / 度量KPI-OKR / 分析改善 / 智能体
-- 文档: docs/plm-complete-redesign-lighting-sme-v1.1.md
-- 说明: 草案可评审；与现网 sys_sequence/sys_dict 并存演进
-- =============================================================================

-- ---------- 1. 自动编号规则 ----------
CREATE TABLE IF NOT EXISTS plm_code_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(64) NOT NULL UNIQUE,
    object_type     VARCHAR(32) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    version_no      VARCHAR(16) NOT NULL DEFAULT 'V1',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    pattern         VARCHAR(256) NOT NULL,
    seq_key         VARCHAR(64) NOT NULL,
    reset_policy    VARCHAR(16) NOT NULL DEFAULT 'NEVER',
    uniqueness_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    preview_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allow_manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    override_roles  VARCHAR(256),
    effective_from  TIMESTAMPTZ,
    effective_to    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_code_rule_obj ON plm_code_rule(object_type, status);

CREATE TABLE IF NOT EXISTS plm_code_rule_segment (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT NOT NULL REFERENCES plm_code_rule(id) ON DELETE CASCADE,
    seq_no          INT NOT NULL,
    segment_type    VARCHAR(16) NOT NULL,
    value_expr      VARCHAR(256),
    pad_length      INT,
    dict_type       VARCHAR(64),
    field_path      VARCHAR(128),
    transform       VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS plm_code_issue_log (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64),
    generated_code  VARCHAR(128) NOT NULL,
    rule_id         BIGINT,
    rule_version    VARCHAR(16),
    context_json    JSONB,
    issuer_id       BIGINT,
    source          VARCHAR(16) NOT NULL DEFAULT 'UI',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_code_issue_code ON plm_code_issue_log(generated_code);
CREATE INDEX IF NOT EXISTS idx_code_issue_obj ON plm_code_issue_log(object_type, object_id);

-- ---------- 2. 数据自检 ----------
CREATE TABLE IF NOT EXISTS plm_dq_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    object_type     VARCHAR(32) NOT NULL,
    phase_scope     VARCHAR(128),
    lifecycle_scope VARCHAR(128),
    severity        VARCHAR(8) NOT NULL,
    check_type      VARCHAR(16) NOT NULL,
    expression      TEXT NOT NULL,
    message_template VARCHAR(512),
    fix_hint        VARCHAR(512),
    auto_fix_code  VARCHAR(64),
    lean_waste_tag  VARCHAR(32),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    version_no      VARCHAR(16) NOT NULL DEFAULT 'V1',
    owner_role      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dq_rule_obj ON plm_dq_rule(object_type, enabled);

CREATE TABLE IF NOT EXISTS plm_dq_run (
    id              BIGSERIAL PRIMARY KEY,
    batch_no        VARCHAR(64),
    trigger_type    VARCHAR(16) NOT NULL,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    rule_id         BIGINT REFERENCES plm_dq_rule(id),
    rule_code       VARCHAR(64),
    severity        VARCHAR(8),
    result          VARCHAR(8) NOT NULL,
    message         VARCHAR(1024),
    detail_json     JSONB,
    suggested_fix_json JSONB,
    duration_ms     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dq_run_obj ON plm_dq_run(object_type, object_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dq_run_batch ON plm_dq_run(batch_no);

CREATE TABLE IF NOT EXISTS plm_dq_object_score (
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    score_0_100     NUMERIC(5,2) NOT NULL DEFAULT 0,
    block_count     INT NOT NULL DEFAULT 0,
    warn_count      INT NOT NULL DEFAULT 0,
    info_count      INT NOT NULL DEFAULT 0,
    last_run_at     TIMESTAMPTZ,
    trend_7d        NUMERIC(6,2),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (object_type, object_id)
);

CREATE TABLE IF NOT EXISTS plm_dq_debt (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,
    object_id       VARCHAR(64) NOT NULL,
    rule_code       VARCHAR(64) NOT NULL,
    severity        VARCHAR(8) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    owner_id        BIGINT,
    due_date        DATE,
    waived_by       BIGINT,
    waive_reason    VARCHAR(512),
    message         VARCHAR(1024),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_dq_debt_status ON plm_dq_debt(status, severity);

-- ---------- 3. 度量 / KPI / OKR ----------
CREATE TABLE IF NOT EXISTS plm_metric_def (
    id              BIGSERIAL PRIMARY KEY,
    metric_code     VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    category        VARCHAR(32) NOT NULL,
    unit            VARCHAR(32),
    direction       VARCHAR(16) NOT NULL DEFAULT 'HIGHER_BETTER',
    calc_type       VARCHAR(16) NOT NULL,
    calc_expr       TEXT,
    grain           VARCHAR(16) NOT NULL DEFAULT 'DAY',
    dimensions_json JSONB,
    source_events   VARCHAR(512),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_metric_value (
    id              BIGSERIAL PRIMARY KEY,
    metric_code     VARCHAR(64) NOT NULL,
    grain_time      DATE NOT NULL,
    dim_json        JSONB,
    object_type     VARCHAR(32),
    object_id       VARCHAR(64),
    value_num       NUMERIC(18,4),
    value_den       NUMERIC(18,4),
    value_calc      NUMERIC(18,4) NOT NULL,
    batch_no        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_metric_val ON plm_metric_value(metric_code, grain_time);
CREATE INDEX IF NOT EXISTS idx_metric_val_obj ON plm_metric_value(object_type, object_id);

CREATE TABLE IF NOT EXISTS plm_kpi_def (
    id              BIGSERIAL PRIMARY KEY,
    kpi_code        VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    metric_code     VARCHAR(64) NOT NULL,
    owner_role      VARCHAR(64),
    owner_user_id   BIGINT,
    org_id          BIGINT,
    target_type     VARCHAR(16) NOT NULL DEFAULT 'ABSOLUTE',
    period_type     VARCHAR(16) NOT NULL DEFAULT 'MONTH',
    threshold_green NUMERIC(18,4),
    threshold_yellow NUMERIC(18,4),
    threshold_red   NUMERIC(18,4),
    linked_okr_kr_id BIGINT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_kpi_value (
    id              BIGSERIAL PRIMARY KEY,
    kpi_code        VARCHAR(64) NOT NULL,
    period_key      VARCHAR(32) NOT NULL,
    target_value    NUMERIC(18,4),
    actual_value    NUMERIC(18,4),
    score           NUMERIC(6,2),
    status          VARCHAR(16),
    dim_json        JSONB,
    comment         VARCHAR(1024),
    locked          BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (kpi_code, period_key)
);

CREATE TABLE IF NOT EXISTS plm_kpi_target_hist (
    id              BIGSERIAL PRIMARY KEY,
    kpi_code        VARCHAR(64) NOT NULL,
    period_key      VARCHAR(32) NOT NULL,
    old_target      NUMERIC(18,4),
    new_target      NUMERIC(18,4),
    changed_by      BIGINT,
    reason          VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_okr_cycle (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_okr_objective (
    id              BIGSERIAL PRIMARY KEY,
    cycle_id        BIGINT NOT NULL REFERENCES plm_okr_cycle(id),
    org_id          BIGINT,
    owner_id        BIGINT,
    title           VARCHAR(256) NOT NULL,
    level           VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ON_TRACK',
    progress_pct    NUMERIC(5,2) DEFAULT 0,
    parent_id       BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_okr_key_result (
    id              BIGSERIAL PRIMARY KEY,
    objective_id    BIGINT NOT NULL REFERENCES plm_okr_objective(id) ON DELETE CASCADE,
    title           VARCHAR(256) NOT NULL,
    metric_code     VARCHAR(64),
    kpi_code        VARCHAR(64),
    baseline        NUMERIC(18,4),
    target          NUMERIC(18,4),
    current_value   NUMERIC(18,4),
    unit            VARCHAR(32),
    weight          NUMERIC(5,2) DEFAULT 1,
    status          VARCHAR(16) DEFAULT 'ON_TRACK',
    last_sync_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_okr_checkin (
    id              BIGSERIAL PRIMARY KEY,
    kr_id           BIGINT NOT NULL REFERENCES plm_okr_key_result(id) ON DELETE CASCADE,
    checkin_date    DATE NOT NULL,
    value           NUMERIC(18,4),
    confidence      INT,
    note            TEXT,
    user_id         BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_metric_lineage (
    id              BIGSERIAL PRIMARY KEY,
    metric_code     VARCHAR(64) NOT NULL,
    source_table    VARCHAR(128),
    source_event    VARCHAR(128),
    transform_desc  TEXT
);

-- ---------- 4. 分析洞察 ----------
CREATE TABLE IF NOT EXISTS plm_analytics_dataset (
    id              BIGSERIAL PRIMARY KEY,
    dataset_code    VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    sql_text        TEXT NOT NULL,
    cache_ttl_sec   INT DEFAULT 300,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS plm_analytics_chart (
    id              BIGSERIAL PRIMARY KEY,
    chart_code      VARCHAR(64) NOT NULL UNIQUE,
    title           VARCHAR(128) NOT NULL,
    dataset_code    VARCHAR(64) NOT NULL,
    chart_type      VARCHAR(32) NOT NULL,
    encode_json     JSONB,
    default_filters JSONB,
    refresh_cron    VARCHAR(64),
    owner_role      VARCHAR(64),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS plm_analytics_insight (
    id              BIGSERIAL PRIMARY KEY,
    insight_no      VARCHAR(64) NOT NULL UNIQUE,
    source          VARCHAR(16) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    severity        VARCHAR(16) NOT NULL,
    category        VARCHAR(64),
    finding_json    JSONB,
    root_cause_json JSONB,
    recommendation_json JSONB,
    related_kpi_codes VARCHAR(512),
    related_object_refs JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
    agent_session_id BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_insight_status ON plm_analytics_insight(status, severity);

-- ---------- 5. 问题改善闭环 ----------
CREATE TABLE IF NOT EXISTS plm_issue (
    id              BIGSERIAL PRIMARY KEY,
    issue_no        VARCHAR(64) NOT NULL UNIQUE,
    source_type     VARCHAR(32) NOT NULL,
    source_ref      VARCHAR(128),
    object_type     VARCHAR(32),
    object_id       VARCHAR(64),
    title           VARCHAR(256) NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    severity        VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    owner_id        BIGINT,
    dept_id         BIGINT,
    due_date        DATE,
    lean_waste_tag  VARCHAR(32),
    kpi_codes       VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_issue_status ON plm_issue(status, severity);

CREATE TABLE IF NOT EXISTS plm_issue_analysis (
    id              BIGSERIAL PRIMARY KEY,
    issue_id        BIGINT NOT NULL REFERENCES plm_issue(id) ON DELETE CASCADE,
    method          VARCHAR(32) NOT NULL,
    content_json    JSONB NOT NULL,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_improve_action (
    id              BIGSERIAL PRIMARY KEY,
    action_no       VARCHAR(64) NOT NULL UNIQUE,
    issue_id        BIGINT NOT NULL REFERENCES plm_issue(id) ON DELETE CASCADE,
    title           VARCHAR(256) NOT NULL,
    action_type     VARCHAR(32) NOT NULL,
    owner_id        BIGINT,
    due_date        DATE,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    evidence_url    VARCHAR(512),
    standard_doc_id BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    done_at         TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS plm_improve_result (
    id              BIGSERIAL PRIMARY KEY,
    issue_id        BIGINT NOT NULL REFERENCES plm_issue(id) ON DELETE CASCADE,
    kpi_code        VARCHAR(64) NOT NULL,
    before_value    NUMERIC(18,4),
    after_value     NUMERIC(18,4),
    window_from     DATE,
    window_to       DATE,
    verified_by     BIGINT,
    verified_at     TIMESTAMPTZ,
    effective       BOOLEAN
);

CREATE TABLE IF NOT EXISTS plm_standard_work (
    id              BIGSERIAL PRIMARY KEY,
    sw_no           VARCHAR(64) NOT NULL UNIQUE,
    title           VARCHAR(256) NOT NULL,
    object_type     VARCHAR(32),
    content         TEXT,
    version_no      VARCHAR(16) NOT NULL DEFAULT 'V1',
    from_issue_id   BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- 6. 智能体 ----------
CREATE TABLE IF NOT EXISTS plm_agent_def (
    id              BIGSERIAL PRIMARY KEY,
    agent_code      VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    model_config_json JSONB,
    tool_allowlist_json JSONB,
    permission_scope VARCHAR(64),
    autonomy_level  VARCHAR(8) NOT NULL DEFAULT 'L0',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    version_no      VARCHAR(16) NOT NULL DEFAULT 'V1',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_agent_session (
    id              BIGSERIAL PRIMARY KEY,
    session_no      VARCHAR(64) NOT NULL UNIQUE,
    agent_code      VARCHAR(64) NOT NULL,
    user_id         BIGINT,
    channel         VARCHAR(16) NOT NULL DEFAULT 'UI',
    context_json    JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS plm_agent_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES plm_agent_session(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT,
    token_usage     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_msg_session ON plm_agent_message(session_id);

CREATE TABLE IF NOT EXISTS plm_agent_tool_call (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES plm_agent_session(id) ON DELETE CASCADE,
    message_id      BIGINT,
    tool_name       VARCHAR(128) NOT NULL,
    request_json    JSONB,
    response_json   JSONB,
    success         BOOLEAN,
    duration_ms     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_agent_action (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT REFERENCES plm_agent_session(id),
    action_type     VARCHAR(32) NOT NULL,
    object_type     VARCHAR(32),
    object_id       VARCHAR(64),
    payload_json    JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    decided_by      BIGINT,
    decided_at      TIMESTAMPTZ,
    result_json     JSONB,
    estimated_minutes_saved INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_action_status ON plm_agent_action(status);

CREATE TABLE IF NOT EXISTS plm_agent_feedback (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT,
    action_id       BIGINT,
    score           INT,
    comment         VARCHAR(1024),
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plm_agent_knowledge (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(256) NOT NULL,
    category        VARCHAR(64),
    content         TEXT NOT NULL,
    source_ref      VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- 7. 待办拉动 / 领域事件 ----------
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
    escalated_to    BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_work_item_owner ON plm_work_item(owner_id, status);

CREATE TABLE IF NOT EXISTS plm_domain_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    event_type      VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(32),
    aggregate_id    VARCHAR(64),
    payload_json    JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_domain_event_status ON plm_domain_event(status, created_at);

-- ---------- 8. 种子：智能体定义 ----------
INSERT INTO plm_agent_def (agent_code, name, description, permission_scope, autonomy_level, tool_allowlist_json)
VALUES
('AGENT_CODER',  '编码助手', '预览与申请业务编号', 'ENGINEER', 'L1', '["codegen.preview","codegen.allocate"]'),
('AGENT_QA',     '质检官', '执行数据自检并建议修复', 'ENGINEER', 'L1', '["dq.run","dq.score","dq.autofix"]'),
('AGENT_ANALYST','分析师', '异常洞察与图表解读', 'RD_LEAD', 'L0', '["metric.query","analytics.insight"]'),
('AGENT_COACH',  '改善教练', '5Why与改善动作拆解', 'RD_LEAD', 'L1', '["issue.create","issue.analyze","improve.draft"]'),
('AGENT_ECN',    '变更助理', '影响面与ECN草稿', 'ENGINEER', 'L1', '["bom.diff","bom.whereused","ecn.draft"]'),
('AGENT_GATE',   '技转检查员', '齐套与放行风险', 'PROCESS', 'L0', '["dq.run","archive.checklist","project.gate"]'),
('AGENT_BOM',    '结构助理', 'BOM规范与重复件', 'ENGINEER', 'L1', '["bom.validate","bom.extract"]'),
('AGENT_HELP',   '问答员', '制度与标准作业问答', 'ENGINEER', 'L0', '["knowledge.search"]'),
('AGENT_ORCH',   '总控', '意图路由与多代理协作', 'ENGINEER', 'L0', '["*"]')
ON CONFLICT (agent_code) DO NOTHING;

-- ---------- 9. 种子：核心 KPI 目录（metric 简化同码） ----------
INSERT INTO plm_metric_def (metric_code, name, category, unit, direction, calc_type, grain) VALUES
('M_DQ_SCORE',        '主数据质量均分', 'QUALITY', 'score', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_CODE_AUTO_RATE',  '自动编号率', 'LEAN', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_ECN_CYCLE_H',     'ECN平均闭环小时', 'CYCLE', 'h', 'LOWER_BETTER', 'SQL', 'DAY'),
('M_GATE_OTD',        '阶段门准时率', 'DELIVERY', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_BOM_REWORK_7D',   'BOM发布后7日回退', 'QUALITY', 'count', 'LOWER_BETTER', 'SQL', 'DAY'),
('M_DOC_COMPLETE',    '技转一次齐套率', 'QUALITY', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_WIP_DRAFT',       '超龄草稿料号数', 'LEAN', 'count', 'LOWER_BETTER', 'SQL', 'DAY'),
('M_AGENT_ADOPT',     '智能体建议采纳率', 'AGENT', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_IMPROVE_CLOSE',   '改善单按时关闭率', 'LEAN', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_CBOM_SYNC',       'EBMS结构同源率', 'QUALITY', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_LOCK_VER',        '大货订单锁版本率', 'DELIVERY', '%', 'HIGHER_BETTER', 'SQL', 'DAY'),
('M_OUTSRC_LEAK',     '外协风险下载次数', 'SAFETY', 'count', 'LOWER_BETTER', 'SQL', 'DAY')
ON CONFLICT (metric_code) DO NOTHING;

INSERT INTO plm_kpi_def (kpi_code, name, metric_code, period_type, threshold_green, threshold_yellow, threshold_red) VALUES
('KPI_DQ_SCORE',      '主数据质量均分', 'M_DQ_SCORE', 'WEEK', 90, 80, 70),
('KPI_CODE_AUTO_RATE','自动编号率', 'M_CODE_AUTO_RATE', 'MONTH', 98, 90, 80),
('KPI_ECN_CYCLE_H',   'ECN平均闭环小时', 'M_ECN_CYCLE_H', 'MONTH', 72, 120, 168),
('KPI_GATE_OTD',      '阶段门准时率', 'M_GATE_OTD', 'MONTH', 85, 70, 60),
('KPI_BOM_REWORK',    'BOM发布后回退', 'M_BOM_REWORK_7D', 'MONTH', 2, 5, 10),
('KPI_DOC_COMPLETE',  '技转一次齐套率', 'M_DOC_COMPLETE', 'MONTH', 90, 80, 70),
('KPI_WIP_DRAFT',     '超龄草稿料号', 'M_WIP_DRAFT', 'WEEK', 20, 50, 100),
('KPI_AGENT_ADOPT',   '智能体采纳率', 'M_AGENT_ADOPT', 'MONTH', 60, 40, 20),
('KPI_IMPROVE_CLOSE', '改善按时关闭率', 'M_IMPROVE_CLOSE', 'MONTH', 85, 70, 50),
('KPI_CBOM_SYNC',     '结构同源率', 'M_CBOM_SYNC', 'WEEK', 95, 90, 80),
('KPI_LOCK_VER',      '订单锁版本率', 'M_LOCK_VER', 'MONTH', 98, 90, 80),
('KPI_OUTSRC_LEAK',   '外协风险事件', 'M_OUTSRC_LEAK', 'MONTH', 0, 1, 3)
ON CONFLICT (kpi_code) DO NOTHING;

-- ---------- 10. 种子：部分 DQ 规则 ----------
INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, lean_waste_tag) VALUES
('PART_NAME_REQUIRED', '名称必填', 'PART', 'BLOCK', 'FIELD', 'nameZh != null && nameZh != ""', '中文名称不能为空', '缺陷'),
('PART_CODE_FROM_GEN', '料号须系统生成', 'PART', 'BLOCK', 'SCRIPT', 'codeIssuedBySystem(partNo)', '料号必须通过自动编号生成', '缺陷'),
('PART_FG_IP', '成品IP等级', 'PART', 'WARN', 'FIELD', 'category!="PRODUCT" || ipRating', '成品建议填写IP防护等级', '过度加工'),
('BOM_QTY_POSITIVE', '用量大于0', 'BOM', 'BLOCK', 'SQL', 'qty > 0', 'BOM用量必须大于0', '缺陷'),
('BOM_NO_CYCLE', '禁止循环引用', 'BOM', 'BLOCK', 'SCRIPT', 'noCycle(bomId)', 'BOM存在循环引用', '缺陷'),
('RELEASE_HAS_DQ_PASS', '发布前无阻断', 'PART', 'BLOCK', 'SCRIPT', 'blockCount==0', '存在阻断级质量问题，禁止发布', '缺陷'),
('ECN_IMPACT_REQUIRED', 'ECN影响面', 'ECN', 'BLOCK', 'FIELD', 'impacts.size()>0', '生效前必须填写影响对象', '缺陷'),
('WIP_DRAFT_LIMIT', '草稿WIP限制', 'PART', 'WARN', 'SCRIPT', 'userDraftCount < 30', '个人草稿料号过多，请先清理', '库存')
ON CONFLICT (rule_code) DO NOTHING;

COMMENT ON TABLE plm_code_rule IS 'V1.1 自动编号规则';
COMMENT ON TABLE plm_dq_rule IS 'V1.1 数据自检规则';
COMMENT ON TABLE plm_metric_def IS 'V1.1 原子指标目录';
COMMENT ON TABLE plm_kpi_def IS 'V1.1 KPI定义-对接OKR';
COMMENT ON TABLE plm_agent_def IS 'V1.1 智能体定义';
COMMENT ON TABLE plm_issue IS 'V1.1 问题单-改善闭环';
COMMENT ON TABLE plm_domain_event IS 'V1.1 领域事件Outbox';
