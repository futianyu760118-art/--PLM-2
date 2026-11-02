-- =============================================================================
-- 59_m04_core_gap.sql
-- M04 研发自治中心（RDC）核心缺口补齐
-- 依据：AEOS V7.2.2 M04 RDC 方案 Own 范围 + 老 PLM V14.3 实际对象模型（实测）
-- 覆盖：需求管理 / 技术风险 / 文档中心 / 审批链配置 / ECR 变更申请 / 老系统迁移追溯
-- 原则：幂等（IF NOT EXISTS）、不改动既有表结构（除 ecn 增列）、全部可回滚
-- =============================================================================

-- ---------- 1. 需求管理 Requirement（AEOS M04 Own，原缺）----------
CREATE TABLE IF NOT EXISTS plm_requirement (
    id                  BIGSERIAL PRIMARY KEY,
    req_no              VARCHAR(64) NOT NULL UNIQUE,           -- 需求编号（走编码规则 rule_code=REQ）
    project_no          VARCHAR(64),                           -- 归属项目
    part_no             VARCHAR(64),                           -- 归属料号（可选）
    title               VARCHAR(300) NOT NULL,
    req_type            VARCHAR(32) NOT NULL DEFAULT 'FUNCTION',
    -- FUNCTION 功能 / PERFORMANCE 性能 / REGULATORY 法规 / CUSTOMER 客户 / PROCESS 工艺 / COST 成本
    priority            VARCHAR(8) NOT NULL DEFAULT 'P2',      -- P0 / P1 / P2
    source              VARCHAR(64),                           -- 客户 / 市场 / 法规 / 内部改善
    source_ref          VARCHAR(128),                          -- 客户需求单号 / 法规条款号
    description         TEXT,
    acceptance_criteria TEXT,                                  -- 验收判据（可量化）
    status              VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / REVIEW / APPROVED / REJECTED / IMPLEMENTED / CLOSED
    owner               VARCHAR(64),
    due_date            DATE,
    approved_by         VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    created_by          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted             SMALLINT NOT NULL DEFAULT 0,
    remark              TEXT,
    CONSTRAINT ck_req_status CHECK (status IN ('DRAFT','REVIEW','APPROVED','REJECTED','IMPLEMENTED','CLOSED')),
    CONSTRAINT ck_req_priority CHECK (priority IN ('P0','P1','P2'))
);
CREATE INDEX IF NOT EXISTS idx_req_project ON plm_requirement(project_no);
CREATE INDEX IF NOT EXISTS idx_req_part ON plm_requirement(part_no);
CREATE INDEX IF NOT EXISTS idx_req_status ON plm_requirement(status);
COMMENT ON TABLE plm_requirement IS 'M04 研发需求（AEOS M04 Own 对象）';

-- 需求追溯链：需求 ↔ 项目 / BOM 节点 / 料号 / 变更 / 测试 / 风险
CREATE TABLE IF NOT EXISTS plm_requirement_link (
    id          BIGSERIAL PRIMARY KEY,
    req_id      BIGINT NOT NULL REFERENCES plm_requirement(id) ON DELETE CASCADE,
    target_type VARCHAR(24) NOT NULL,
    -- PROJECT / BOM_NODE / PART / ECN / ECR / TEST / RISK / TASK
    target_no   VARCHAR(64) NOT NULL,
    link_type   VARCHAR(24) NOT NULL DEFAULT 'IMPLEMENTS',
    -- IMPLEMENTS 实现 / VERIFIES 验证 / AFFECTS 影响 / DERIVES 派生
    created_by  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (req_id, target_type, target_no, link_type)
);
CREATE INDEX IF NOT EXISTS idx_reqlink_target ON plm_requirement_link(target_type, target_no);
COMMENT ON TABLE plm_requirement_link IS '需求追溯链，支撑 M04 验收"需求→设计→验证可关联"';

-- ---------- 2. 技术风险 TechnicalRisk（AEOS M04 核心对象，原缺）----------
CREATE TABLE IF NOT EXISTS plm_technical_risk (
    id              BIGSERIAL PRIMARY KEY,
    risk_no         VARCHAR(64) NOT NULL UNIQUE,               -- 风险编号（rule_code=RISK）
    project_no      VARCHAR(64),
    part_no         VARCHAR(64),
    title           VARCHAR(300) NOT NULL,
    category        VARCHAR(32) NOT NULL DEFAULT 'STRUCTURE',
    -- STRUCTURE 结构 / ELECTRONIC 电子 / PROCESS 工艺 / SUPPLY 供应链 / REGULATORY 法规 / SCHEDULE 进度
    description     TEXT,
    probability     SMALLINT NOT NULL DEFAULT 3,               -- 1-5
    impact          SMALLINT NOT NULL DEFAULT 3,               -- 1-5
    risk_level      VARCHAR(8) GENERATED ALWAYS AS (
                        CASE WHEN probability * impact >= 15 THEN 'HIGH'
                             WHEN probability * impact >= 8  THEN 'MEDIUM'
                             ELSE 'LOW' END) STORED,
    mitigation      TEXT,                                      -- 缓解措施
    contingency     TEXT,                                      -- 应急方案
    owner           VARCHAR(64),
    due_date        DATE,
    status          VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    -- OPEN / MITIGATING / CLOSED / ACCEPTED
    linked_req_id   BIGINT REFERENCES plm_requirement(id) ON DELETE SET NULL,
    linked_ecn_no   VARCHAR(64),
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_risk_prob CHECK (probability BETWEEN 1 AND 5),
    CONSTRAINT ck_risk_impact CHECK (impact BETWEEN 1 AND 5),
    CONSTRAINT ck_risk_status CHECK (status IN ('OPEN','MITIGATING','CLOSED','ACCEPTED'))
);
CREATE INDEX IF NOT EXISTS idx_risk_project ON plm_technical_risk(project_no);
CREATE INDEX IF NOT EXISTS idx_risk_level ON plm_technical_risk(risk_level, status);
COMMENT ON TABLE plm_technical_risk IS 'M04 技术风险（AEOS M04 核心对象 TechnicalRisk）';

-- ---------- 3. 文档中心（老 PLM 唯一不可替代资产，新系统原只有料号档案树）----------
CREATE TABLE IF NOT EXISTS plm_document (
    id              BIGSERIAL PRIMARY KEY,
    doc_no          VARCHAR(64) NOT NULL UNIQUE,               -- 文档编号（rule_code=DOC）
    title           VARCHAR(300) NOT NULL,
    doc_type        VARCHAR(32) NOT NULL DEFAULT 'DRAWING',
    -- DRAWING 图纸 / STANDARD 标准 / PROCESS 工艺 / SPEC 规格书 / MANUAL 说明书 / SYSTEM 体系文件 / OTHER
    part_no         VARCHAR(64),                               -- 可空：体系文件不挂料号
    project_no      VARCHAR(64),
    archive_node_id BIGINT,                                    -- 关联 plm_archive_tree 节点（料号档案目录）
    lifecycle_state VARCHAR(24) NOT NULL DEFAULT 'WORKING',
    -- WORKING 工作区 / RELEASED 发布区 / ARCHIVED 归档区 / OBSOLETE 报废区 / RECYCLE 回收区
    current_version VARCHAR(32) NOT NULL DEFAULT 'V1.0',
    owner           VARCHAR(64),
    secret_level    VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',    -- PUBLIC / INTERNAL / SECRET
    checked_out_by  VARCHAR(64),                               -- 检入检出：当前锁定人
    checked_out_at  TIMESTAMPTZ,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    remark          TEXT,
    CONSTRAINT ck_doc_state CHECK (lifecycle_state IN ('WORKING','RELEASED','ARCHIVED','OBSOLETE','RECYCLE'))
);
CREATE INDEX IF NOT EXISTS idx_doc_part ON plm_document(part_no);
CREATE INDEX IF NOT EXISTS idx_doc_project ON plm_document(project_no);
CREATE INDEX IF NOT EXISTS idx_doc_state ON plm_document(lifecycle_state, doc_type);
COMMENT ON TABLE plm_document IS 'M04 文档实体（对齐老 PLM 五区生命周期 + 检入检出）';

CREATE TABLE IF NOT EXISTS plm_document_version (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT NOT NULL REFERENCES plm_document(id) ON DELETE CASCADE,
    version         VARCHAR(32) NOT NULL,
    revision        VARCHAR(32),                               -- 小版本（老 PLM 的 cp一位数 / V0-V9 序列）
    file_id         BIGINT,                                    -- 关联 plm_file.id
    file_name       VARCHAR(300),
    file_size       BIGINT,
    checksum        VARCHAR(64),                               -- SHA-256，迁移校验用
    change_note     TEXT,
    state           VARCHAR(24) NOT NULL DEFAULT 'WORKING',
    approved_by     VARCHAR(64),
    approved_at     TIMESTAMPTZ,
    is_current      SMALLINT NOT NULL DEFAULT 0,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_docver ON plm_document_version(doc_id, version, COALESCE(revision, ''));
CREATE INDEX IF NOT EXISTS idx_docver_doc ON plm_document_version(doc_id, is_current);
COMMENT ON TABLE plm_document_version IS '文档版本链，支撑版本回放与老 PLM 迁移校验';

CREATE TABLE IF NOT EXISTS plm_document_acl (
    id           BIGSERIAL PRIMARY KEY,
    doc_id       BIGINT NOT NULL REFERENCES plm_document(id) ON DELETE CASCADE,
    subject_type VARCHAR(16) NOT NULL,                         -- USER / ROLE / DEPT / PROJECT_ROLE
    subject_id   VARCHAR(64) NOT NULL,
    can_read     SMALLINT NOT NULL DEFAULT 1,
    can_write    SMALLINT NOT NULL DEFAULT 0,
    can_checkout SMALLINT NOT NULL DEFAULT 0,
    can_delete   SMALLINT NOT NULL DEFAULT 0,
    granted_by   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doc_id, subject_type, subject_id)
);
COMMENT ON TABLE plm_document_acl IS '文档对象级权限（AEOS M04 安全控制：按项目/岗位/密级隔离）';

-- ---------- 4. 审批链配置化（替代 EcnServiceImpl 硬编码 PENDING_L1/L2）----------
CREATE TABLE IF NOT EXISTS plm_approval_flow (
    id          BIGSERIAL PRIMARY KEY,
    flow_code   VARCHAR(64) NOT NULL UNIQUE,                   -- ECN_DEFAULT / ECR_DEFAULT / REQ_DEFAULT / DOC_RELEASE
    object_type VARCHAR(32) NOT NULL,                          -- ECN / ECR / REQUIREMENT / DOCUMENT / RISK
    name        VARCHAR(128) NOT NULL,
    version_no  VARCHAR(16) NOT NULL DEFAULT 'V1',
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',          -- DRAFT / ENABLED / DISABLED
    remark      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_approval_flow IS '审批流定义，替代硬编码审批级数';

CREATE TABLE IF NOT EXISTS plm_approval_node (
    id             BIGSERIAL PRIMARY KEY,
    flow_id        BIGINT NOT NULL REFERENCES plm_approval_flow(id) ON DELETE CASCADE,
    seq            INT NOT NULL,
    node_code      VARCHAR(64) NOT NULL,
    node_name      VARCHAR(128) NOT NULL,
    approver_type  VARCHAR(24) NOT NULL DEFAULT 'ROLE',
    -- ROLE 角色 / USER 指定人 / POSITION 岗位 / PROJECT_ROLE 项目角色 / DEPT_LEADER 部门主管
    approver_value VARCHAR(256),
    condition_expr VARCHAR(512),                               -- 例：change_type='MATERIAL'
    parallel_group VARCHAR(32),                                -- 同组并行会签
    timeout_hours  INT,
    on_reject      VARCHAR(24) NOT NULL DEFAULT 'BACK',        -- BACK 退回上节点 / TERMINATE 终止 / TO_APPLICANT 退回申请人
    on_timeout     VARCHAR(24) NOT NULL DEFAULT 'REMIND',      -- REMIND 提醒 / AUTO_PASS 自动通过 / ESCALATE 升级
    remark         TEXT,
    UNIQUE (flow_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_apprnode_flow ON plm_approval_node(flow_id, seq);
COMMENT ON TABLE plm_approval_node IS '审批节点定义，支持并行/条件/超时策略';

CREATE TABLE IF NOT EXISTS plm_approval_instance (
    id           BIGSERIAL PRIMARY KEY,
    flow_id      BIGINT NOT NULL REFERENCES plm_approval_flow(id),
    object_type  VARCHAR(32) NOT NULL,
    object_no    VARCHAR(64) NOT NULL,
    current_seq  INT NOT NULL DEFAULT 1,
    status       VARCHAR(24) NOT NULL DEFAULT 'RUNNING',       -- RUNNING / APPROVED / REJECTED / CANCELLED
    started_by   VARCHAR(64),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at  TIMESTAMPTZ,
    UNIQUE (object_type, object_no, flow_id)
);
CREATE INDEX IF NOT EXISTS idx_apprinst_obj ON plm_approval_instance(object_type, object_no);
COMMENT ON TABLE plm_approval_instance IS '审批实例，支撑变更可回放';

CREATE TABLE IF NOT EXISTS plm_approval_task (
    id           BIGSERIAL PRIMARY KEY,
    instance_id  BIGINT NOT NULL REFERENCES plm_approval_instance(id) ON DELETE CASCADE,
    node_seq     INT NOT NULL,
    approver     VARCHAR(64),
    action       VARCHAR(24),                                  -- APPROVE / REJECT / BACK / DELEGATE / TIMEOUT_PASS
    comment      VARCHAR(1000),
    acted_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_apprtask_inst ON plm_approval_task(instance_id, node_seq);
COMMENT ON TABLE plm_approval_task IS '审批动作流水（谁、何时、什么意见）';

-- ---------- 5. ECR 变更申请单（老 PLM 有独立 ECR，新系统原只有 ECN）----------
CREATE TABLE IF NOT EXISTS plm_ecr (
    id              BIGSERIAL PRIMARY KEY,
    ecr_no          VARCHAR(64) NOT NULL UNIQUE,               -- ECR 单号（rule_code=ECR）
    title           VARCHAR(300) NOT NULL,
    part_no         VARCHAR(64),
    project_no      VARCHAR(64),
    change_type     VARCHAR(32) NOT NULL DEFAULT 'MATERIAL',
    -- MATERIAL 物料 / STRUCTURE 结构 / PROCESS 工艺 / DOCUMENT 文档 / BOM BOM结构
    change_reason   TEXT NOT NULL,
    change_content  TEXT,
    impact_assessment TEXT,                                    -- 影响分析（成本/交期/库存/认证）
    urgency         VARCHAR(16) NOT NULL DEFAULT 'NORMAL',     -- URGENT / NORMAL / PLAN
    status          VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / REVIEWING / APPROVED / REJECTED / CONVERTED / CLOSED
    applicant       VARCHAR(64) NOT NULL,
    apply_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by     VARCHAR(64),
    approved_at     TIMESTAMPTZ,
    ecn_no          VARCHAR(64),                               -- 审批通过后生成的 ECN 单号
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ecr_status CHECK (status IN ('DRAFT','REVIEWING','APPROVED','REJECTED','CONVERTED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_ecr_part ON plm_ecr(part_no);
CREATE INDEX IF NOT EXISTS idx_ecr_status ON plm_ecr(status);
COMMENT ON TABLE plm_ecr IS 'ECR 工程变更申请（对齐老 PLM 的 ECR/ECN 双单模型）';

-- ECN 增列：关联来源 ECR（可空，保持向后兼容）
ALTER TABLE plm_ecn ADD COLUMN IF NOT EXISTS ecr_id BIGINT;
ALTER TABLE plm_ecn ADD COLUMN IF NOT EXISTS ecr_no VARCHAR(64);
COMMENT ON COLUMN plm_ecn.ecr_no IS '来源 ECR 单号，空表示直接创建的 ECN';

-- ---------- 6. 老系统迁移追溯（老 PLM / EBMS / K3 对象 → 新对象）----------
CREATE TABLE IF NOT EXISTS plm_legacy_map (
    id            BIGSERIAL PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,                        -- OLD_PLM / EBMS / K3 / SPC
    object_type   VARCHAR(32) NOT NULL,                        -- MATERIAL / DOCUMENT / DOC_VERSION / BOM / PROJECT / USER
    legacy_id     VARCHAR(128) NOT NULL,                       -- 老系统主键或业务号
    legacy_code   VARCHAR(128),                                -- 老系统业务编码（如物料编号/图号）
    new_id        BIGINT,
    new_code      VARCHAR(128),
    checksum      VARCHAR(64),                                 -- 迁移时源数据指纹
    migrate_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',     -- PENDING / DONE / SKIPPED / CONFLICT
    conflict_note TEXT,
    migrated_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_system, object_type, legacy_id)
);
CREATE INDEX IF NOT EXISTS idx_legacy_code ON plm_legacy_map(source_system, object_type, legacy_code);
CREATE INDEX IF NOT EXISTS idx_legacy_status ON plm_legacy_map(migrate_status);
COMMENT ON TABLE plm_legacy_map IS '老 PLM/EBMS/K3 对象到新对象的映射与迁移状态，保证可追溯、可回滚、可对账';

-- =============================================================================
-- 回滚脚本（人工确认后执行）
-- DROP TABLE IF EXISTS plm_legacy_map;
-- DROP TABLE IF EXISTS plm_ecr;
-- ALTER TABLE plm_ecn DROP COLUMN IF EXISTS ecr_id, DROP COLUMN IF EXISTS ecr_no;
-- DROP TABLE IF EXISTS plm_approval_task, plm_approval_instance, plm_approval_node, plm_approval_flow;
-- DROP TABLE IF EXISTS plm_document_acl, plm_document_version, plm_document;
-- DROP TABLE IF EXISTS plm_technical_risk;
-- DROP TABLE IF EXISTS plm_requirement_link, plm_requirement;
-- =============================================================================
