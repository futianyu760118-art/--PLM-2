-- =============================================================================
-- HJ-PLM 增量 26: AEOS 研发自治中心 V0.2 / PLM-2 V1.1 对齐
-- 基线: AEOS研发自治中心数据模型V0.2 + PLM-2改善方案V1.1
-- 目标: 双级审批、Gate命名空间、统一Evidence、研发Metric治理、Action Center基础
-- 说明: 本迁移只冻结“结构与控制基线”；PLM-G0~G8正式映射及业务阈值仍需G2评审后生效
-- =============================================================================

-- ---------- 1. 关键节点双级审批 ----------
CREATE TABLE IF NOT EXISTS plm_project_node_approval (
    id                  BIGSERIAL PRIMARY KEY,
    node_id             BIGINT NOT NULL REFERENCES plm_project_node(id) ON DELETE CASCADE,
    project_id          BIGINT NOT NULL,
    node_code           VARCHAR(32) NOT NULL,
    approval_level      VARCHAR(16) NOT NULL,   -- RD_LEAD / GM
    approval_seq        SMALLINT NOT NULL,      -- 1 / 2
    approval_round      INT NOT NULL DEFAULT 1,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
    submitted_by_id     BIGINT,
    submitted_by_name   VARCHAR(64),
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    comment             TEXT,
    evidence_snapshot   TEXT,
    request_id          VARCHAR(64),
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_node_approval_node
    ON plm_project_node_approval(node_id, approval_round, approval_seq);
CREATE INDEX IF NOT EXISTS idx_node_approval_project
    ON plm_project_node_approval(project_id, node_code, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_node_approval_request
    ON plm_project_node_approval(request_id) WHERE request_id IS NOT NULL;
COMMENT ON TABLE plm_project_node_approval IS 'AEOS研发自治中心：关键节点 RD_LEAD→GM 双级审批记录';

-- ---------- 2. 项目正式变更：Health Score 的 CF 不再使用 edit_count ----------
CREATE TABLE IF NOT EXISTS plm_project_change (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES plm_project(id) ON DELETE CASCADE,
    project_no      VARCHAR(64),
    change_no       VARCHAR(64),
    change_type     VARCHAR(32) NOT NULL, -- PLAN/REQUIREMENT/DESIGN/ECO/SCOPE/DELIVERY
    reason          TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- DRAFT/APPROVED/REJECTED/EFFECTIVE/CANCELLED
    approved_by     VARCHAR(64),
    approved_at     TIMESTAMPTZ,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_project_change_project
    ON plm_project_change(project_id, status, change_type);
COMMENT ON TABLE plm_project_change IS '研发项目正式变更；用于CHANGE_STABILITY_INDEX/CF，不以节点编辑次数替代';

-- ---------- 3. Gate Namespace：避免DEV-G / RD-MG / PLM-G同名混淆 ----------
CREATE TABLE IF NOT EXISTS aeos_gate_namespace (
    namespace_code  VARCHAR(32) PRIMARY KEY,
    namespace_name  VARCHAR(128) NOT NULL,
    purpose         VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version_no      VARCHAR(16) NOT NULL DEFAULT 'V0.1',
    approved_by     VARCHAR(64),
    approved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aeos_gate_rule (
    gate_id             BIGSERIAL PRIMARY KEY,
    namespace_code      VARCHAR(32) NOT NULL REFERENCES aeos_gate_namespace(namespace_code),
    gate_code           VARCHAR(32) NOT NULL,
    gate_name           VARCHAR(128) NOT NULL,
    rule_status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    decision_owner      VARCHAR(64),
    autonomy_level      VARCHAR(8) NOT NULL DEFAULT 'L2',
    rule_version        VARCHAR(16) NOT NULL DEFAULT 'V0.1',
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(namespace_code, gate_code)
);

CREATE TABLE IF NOT EXISTS aeos_gate_node_rule (
    id              BIGSERIAL PRIMARY KEY,
    gate_id         BIGINT NOT NULL REFERENCES aeos_gate_rule(gate_id) ON DELETE CASCADE,
    node_code       VARCHAR(32) NOT NULL,
    required_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    required_status VARCHAR(16) NOT NULL DEFAULT 'DONE',
    UNIQUE(gate_id, node_code)
);

CREATE TABLE IF NOT EXISTS aeos_gate_evidence_rule (
    id              BIGSERIAL PRIMARY KEY,
    gate_id         BIGINT NOT NULL REFERENCES aeos_gate_rule(gate_id) ON DELETE CASCADE,
    evidence_type   VARCHAR(64) NOT NULL,
    min_count       INT NOT NULL DEFAULT 1,
    status_required VARCHAR(16) DEFAULT 'APPROVED'
);

CREATE TABLE IF NOT EXISTS aeos_gate_metric_rule (
    id              BIGSERIAL PRIMARY KEY,
    gate_id         BIGINT NOT NULL REFERENCES aeos_gate_rule(gate_id) ON DELETE CASCADE,
    metric_code     VARCHAR(64) NOT NULL,
    operator        VARCHAR(8) NOT NULL,
    threshold_value NUMERIC(18,4),
    threshold_ref   VARCHAR(128),
    threshold_status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED'
);

INSERT INTO aeos_gate_namespace(namespace_code, namespace_name, purpose, status, version_no)
VALUES
('DEV', 'AEOS开发治理阶段', 'DEV-G0~DEV-G6；用于项目开发放行，不等同产品研发Gate', 'ACTIVE', 'V1.0'),
('RD-MG', '研发业务简化Gate', 'RD-MG1~RD-MG5；数据模型原型中的业务Gate', 'DRAFT', 'V0.2'),
('PLM', 'PLM工程Gate', 'PLM-G0~G8；正式名称、边界及映射待G2冻结', 'PENDING', 'V0.2')
ON CONFLICT (namespace_code) DO NOTHING;

INSERT INTO aeos_gate_rule(namespace_code, gate_code, gate_name, rule_status, decision_owner, autonomy_level, rule_version)
VALUES
('RD-MG','RD-MG1','立项评审','DRAFT','研发负责人','L2','V0.2'),
('RD-MG','RD-MG2','设计输入冻结','DRAFT','研发负责人','L2','V0.2'),
('RD-MG','RD-MG3','样机验证','DRAFT','研发负责人','L2','V0.2'),
('RD-MG','RD-MG4','工程样/模具确认','DRAFT','工程负责人','L2','V0.2'),
('RD-MG','RD-MG5','试产验收','DRAFT','项目决策者','L3','V0.2')
ON CONFLICT (namespace_code, gate_code) DO NOTHING;

-- ---------- 4. AEOS Evidence Registry ----------
CREATE TABLE IF NOT EXISTS aeos_evidence (
    evidence_id         VARCHAR(64) PRIMARY KEY,
    domain              VARCHAR(32) NOT NULL DEFAULT 'R&D',
    project_id          BIGINT,
    product_id          VARCHAR(64),
    product_version     VARCHAR(32),
    object_type         VARCHAR(64) NOT NULL,
    object_id           VARCHAR(128) NOT NULL,
    object_version      VARCHAR(32),
    evidence_type       VARCHAR(64) NOT NULL,
    source_type         VARCHAR(32) NOT NULL,
    source_system       VARCHAR(64) NOT NULL,
    file_id             BIGINT,
    file_uri            VARCHAR(1024),
    content_ref         VARCHAR(1024),
    record_ref          VARCHAR(256),
    hash                VARCHAR(128),
    status              VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    created_by          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by         VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    related_gate        VARCHAR(64),
    related_decision    VARCHAR(64),
    related_action      VARCHAR(64),
    related_result      VARCHAR(64),
    version_no          VARCHAR(16) NOT NULL DEFAULT 'V1'
);
CREATE INDEX IF NOT EXISTS idx_aeos_evidence_object
    ON aeos_evidence(object_type, object_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_aeos_evidence_project
    ON aeos_evidence(project_id, evidence_type, status);

ALTER TABLE plm_project_node_evidence
    ADD COLUMN IF NOT EXISTS aeos_evidence_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_node_evidence_aeos
    ON plm_project_node_evidence(aeos_evidence_id);

-- 文件对象与17层档案采用“引用”，不复制第二份业务真相
CREATE TABLE IF NOT EXISTS aeos_file_object (
    file_object_id      VARCHAR(64) PRIMARY KEY,
    plm_file_id         BIGINT,
    file_name           VARCHAR(256),
    hash                VARCHAR(128),
    source_system       VARCHAR(64) NOT NULL DEFAULT 'PLM-2',
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aeos_archive_reference (
    id                  BIGSERIAL PRIMARY KEY,
    archive_node_id     BIGINT,
    file_object_id      VARCHAR(64) NOT NULL REFERENCES aeos_file_object(file_object_id),
    evidence_id         VARCHAR(64) REFERENCES aeos_evidence(evidence_id),
    object_type         VARCHAR(64) NOT NULL,
    object_id           VARCHAR(128) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- 5. Metric Object治理扩展 ----------
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS business_definition TEXT;
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS metric_owner VARCHAR(64);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS source_entity VARCHAR(128);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS source_data_product VARCHAR(128);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS quality_rule_json JSONB;
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS threshold_json JSONB;
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS threshold_status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED';
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS baseline_value NUMERIC(18,4);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS baseline_period VARCHAR(64);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS target_value NUMERIC(18,4);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS permission_scope VARCHAR(256);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS version_no VARCHAR(16) NOT NULL DEFAULT 'V1';
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS approval_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE plm_metric_def ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;

INSERT INTO plm_metric_def
(metric_code,name,description,category,unit,direction,calc_type,calc_expr,grain,enabled,
 business_definition,metric_owner,source_entity,source_data_product,threshold_status,version_no,approval_status)
VALUES
('NODE_COMPLETION_RATE','节点完成率','DONE节点数/适用节点数','PROJECT','%', 'HIGHER_BETTER','FORMULA','done_nodes/total_nodes','PROJECT',TRUE,
 '项目适用节点的完成比例','研发项目Owner','ProjectNode','Project360','PROPOSED','V0.2','DRAFT'),
('KEY_NODE_COMPLETION_RATE','关键节点完成率','关键节点DONE数/关键节点总数','PROJECT','%', 'HIGHER_BETTER','FORMULA','key_done/key_total','PROJECT',TRUE,
 '七个关键节点的完成比例','研发项目Owner','ProjectNode','Project360','PROPOSED','V0.2','DRAFT'),
('ON_TIME_RATE','节点准时率','按计划日期完成节点数/可评价完成节点数','PROJECT','%', 'HIGHER_BETTER','FORMULA','on_time/done_with_plan','PROJECT',TRUE,
 '有计划日期的完成节点中按期完成的比例','研发项目Owner','ProjectNode','Project360','PROPOSED','V0.2','DRAFT'),
('PROJECT_DATA_COMPLETENESS','项目数据完整率','已填写必填字段数/应填必填字段数','PROJECT','%', 'HIGHER_BETTER','FORMULA','filled_required/required_total','PROJECT',TRUE,
 '按研发项目节点必填字段规则计算完整率','研发数据Owner','ProjectNode','Project360','PROPOSED','V0.2','DRAFT'),
('CHANGE_STABILITY_INDEX','变更稳定指数','MAX(0,1-正式批准变更数/10)','PROJECT','ratio', 'HIGHER_BETTER','FORMULA','max(0,1-approved_changes/10)','PROJECT',TRUE,
 '反映项目正式变更稳定性；严禁以edit_count替代','工程负责人','ProjectChange','Change360','PROPOSED','V0.2','DRAFT'),
('PROJECT_HEALTH_SCORE','项目健康分','100*(0.35*NCR+0.25*KCR+0.20*OTR+0.10*CR+0.10*CF)','PROJECT','score',
 'HIGHER_BETTER','FORMULA','100*(0.35*NCR+0.25*KCR+0.20*OTR+0.10*CR+0.10*CF)','PROJECT',TRUE,
 '研发项目综合健康分；子指标必须来自已注册Metric Object','研发负责人','Project','Project360','PROPOSED','V0.2','DRAFT')
ON CONFLICT (metric_code) DO NOTHING;

-- ---------- 6. R&D Action Center 数据底座 ----------
CREATE TABLE IF NOT EXISTS aeos_rd_action (
    action_id           VARCHAR(64) PRIMARY KEY,
    source_type         VARCHAR(32) NOT NULL, -- MEETING/METRIC/ISSUE/AGENT/CUSTOMER/GATE/MANAGEMENT
    source_id           VARCHAR(128),
    domain              VARCHAR(32) NOT NULL DEFAULT 'R&D',
    project_id          BIGINT,
    object_type         VARCHAR(64),
    object_id           VARCHAR(128),
    object_version      VARCHAR(32),
    title               VARCHAR(256) NOT NULL,
    description         TEXT,
    owner               VARCHAR(64),
    assigned_by         VARCHAR(64),
    assigned_at         TIMESTAMPTZ,
    due_at              TIMESTAMPTZ,
    priority            VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status              VARCHAR(24) NOT NULL DEFAULT 'NEW',
    acceptance_required BOOLEAN NOT NULL DEFAULT TRUE,
    evidence_id         VARCHAR(64),
    result_ref          VARCHAR(128),
    evaluation_ref      VARCHAR(128),
    learning_ref        VARCHAR(128),
    request_id          VARCHAR(64),
    trace_id            VARCHAR(64),
    created_by          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_rd_action_request
    ON aeos_rd_action(request_id) WHERE request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rd_action_status
    ON aeos_rd_action(status, owner, due_at);
CREATE INDEX IF NOT EXISTS idx_rd_action_object
    ON aeos_rd_action(object_type, object_id);

CREATE TABLE IF NOT EXISTS aeos_rd_action_acceptance (
    id              BIGSERIAL PRIMARY KEY,
    action_id       VARCHAR(64) NOT NULL REFERENCES aeos_rd_action(action_id) ON DELETE CASCADE,
    submitter       VARCHAR(64),
    acceptor        VARCHAR(64),
    result          VARCHAR(16), -- ACCEPTED/REJECTED
    evidence_id     VARCHAR(64),
    comment         TEXT,
    submitted_at    TIMESTAMPTZ,
    decided_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS aeos_rd_action_history (
    id              BIGSERIAL PRIMARY KEY,
    action_id       VARCHAR(64) NOT NULL REFERENCES aeos_rd_action(action_id) ON DELETE CASCADE,
    from_status     VARCHAR(24),
    to_status       VARCHAR(24) NOT NULL,
    actor           VARCHAR(64),
    reason          VARCHAR(512),
    trace_id        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- 7. 权限：先建“能力”，G2再冻结角色映射 ----------
INSERT INTO sys_permission(parent_id, perm_code, perm_name, perm_type, sort_order)
VALUES
(0,'project:node:submit','关键节点提交验收',3,1),
(0,'project:node:approve:rd','关键节点研发主管审批',3,2),
(0,'project:node:approve:gm','关键节点总经理审批',3,3),
(0,'rd:action:edit','研发事项创建与执行',3,10),
(0,'rd:action:accept','研发事项独立验收',3,11),
(0,'rd:action:close','研发事项关闭',3,12)
ON CONFLICT (perm_code) DO NOTHING;

-- ADMIN自动取得新增权限；ENGINEER只自动取得“提交验收”和事项执行，不自动获得审批权
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.perm_code IN ('project:node:submit','project:node:approve:rd','project:node:approve:gm',
                      'rd:action:edit','rd:action:accept','rd:action:close')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ENGINEER'
  AND p.perm_code IN ('project:node:submit','rd:action:edit')
ON CONFLICT DO NOTHING;

-- G2要求：研发主管/总经理的权限映射必须由项目决策者在正式角色矩阵中确认，不在本迁移中擅自新增组织角色。
