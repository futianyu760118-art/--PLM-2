-- =============================================================================
-- HJ-PLM 增量 34: M04 研发自治中心 — 决策-执行闭环层 (DEH)
-- 依据 AEOS: contracts/DEH_CONTRACT_V1.md §3 Decision / §4 Action / §5 Result
--            contracts/CONTRACT_PACK_V1.md §6 Decision-Action
--            architecture/DEH_BOUNDARY_V1.md (Fact→Event→DecisionCase→Decision→Action→Result→Verify→Evidence)
--            架构方案 V0.2 §3.7 / §5.1 / §5.7 (自治闭环 + 唯一事实源)
-- 覆盖功能点: F01 唯一事实源 / F16 决策-学习闭环；并为 F09/F15 穿透样例补齐
--             「管理结果 → 决策 → 动作 → 结果 → 验证 → 原始记录」的中间段。
-- 说明: 本脚本幂等(IF NOT EXISTS / ON CONFLICT DO NOTHING), 可重复执行。
-- =============================================================================

-- =============================================================================
-- 1. 唯一事实源裁定 (F01 / 架构 V0.2 §5.1)
--    每类对象有且仅有一个 Accountable Owner; system_lead 与验收人分离。
--    UNIQUE(object_type) 是「不得存在第二事实源」在结构上的落点:
--    同类对象无法登记出第二个 Owner。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_object_owner (
    id             BIGSERIAL PRIMARY KEY,
    object_type    VARCHAR(32) NOT NULL UNIQUE,   -- PART/DRAWING/BOM/ECO/RELEASE/EVIDENCE/...
    owner_role     VARCHAR(64) NOT NULL,          -- Accountable Business Owner(角色位→姓名)
    owner_id       VARCHAR(64),
    system_lead_id VARCHAR(64),                   -- 系统负责人(≠验收人)
    effective_date DATE,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUPERSEDED
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_object_owner IS '唯一事实源裁定: 每类对象唯一 Accountable Owner(F01)';

-- =============================================================================
-- 2. Decision 契约 (DEH_CONTRACT_V1 §3)
--    Decision 只保存引用(source_type/source_id), 不拥有上游对象主数据。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_decision (
    decision_id       VARCHAR(64) PRIMARY KEY,
    decision_case_id  VARCHAR(64),
    goal_id           VARCHAR(64),
    source_type       VARCHAR(16) NOT NULL,  -- meeting|event|issue|agent|human|system
    source_id         VARCHAR(64) NOT NULL,
    object_type       VARCHAR(32),
    object_id         VARCHAR(64),
    content           TEXT NOT NULL,
    reason            TEXT,
    decision_owner_id VARCHAR(64) NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'draft',  -- draft|confirmed|superseded|cancelled
    decided_at        TIMESTAMPTZ,
    evidence_refs     JSONB,
    created_by        VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_decision_source ON plm_decision(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_decision_status ON plm_decision(status);
COMMENT ON TABLE plm_decision IS 'DEH 决策(Draft→Confirmed; 变更经 superseded 留痕, 不原地改写)';

-- =============================================================================
-- 3. Action 契约补齐 (DEH_CONTRACT_V1 §4 / 架构 §3.7)
--    增量 10 的 plm_agent_action 只有 session/action_type/payload, 缺 DEH 要求的
--    Owner、期限、验证/回退升级路径与决策-结果关联。此处增量加列, 不改动旧列,
--    保证既有 Agent 会话链路继续可读。
--
--    状态取值: 旧值 PROPOSED/APPROVED/REJECTED 保留可读, 与 DEH 取值的对应关系为
--    PROPOSED→candidate, APPROVED→confirmed, REJECTED→cancelled;
--    新写入一律使用 candidate|pending|confirmed|in_progress|blocked|done|cancelled。
--    (不原地 UPDATE 旧行: 迁移期旧消费方仍按旧值读取, 改写会造成静默语义漂移。)
-- =============================================================================
ALTER TABLE plm_agent_action
    ADD COLUMN IF NOT EXISTS action_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS decision_id      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS goal_id          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_type      VARCHAR(16),
    ADD COLUMN IF NOT EXISTS source_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS owner_id         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS owner_role       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS description      TEXT,
    ADD COLUMN IF NOT EXISTS expected_result  JSONB,
    ADD COLUMN IF NOT EXISTS priority         VARCHAR(16) DEFAULT 'medium',  -- low|medium|high|critical
    ADD COLUMN IF NOT EXISTS start_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS due_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verification_rule JSONB,
    ADD COLUMN IF NOT EXISTS escalation_rule  JSONB,
    ADD COLUMN IF NOT EXISTS evidence_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS result_id        VARCHAR(64),   -- 最新 Result(动作→结果)
    ADD COLUMN IF NOT EXISTS evaluation_id    VARCHAR(64);   -- 学习/评价关联

-- DEH §4: Action 必须拥有稳定 action_id。旧行按主键补一个确定性编号(幂等)。
UPDATE plm_agent_action SET action_id = 'ACT-' || id WHERE action_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_action_action_id ON plm_agent_action(action_id);
CREATE INDEX IF NOT EXISTS idx_agent_action_decision ON plm_agent_action(decision_id);
COMMENT ON COLUMN plm_agent_action.action_id IS 'DEH Action 稳定标识(旧行按主键补 ACT-<id>)';
COMMENT ON COLUMN plm_agent_action.result_id IS '最新 Result, 与 plm_agent_result.action_id 互为反向引用';

-- =============================================================================
-- 4. Result 契约 (DEH_CONTRACT_V1 §5)
--    done ≠ verified: 执行者提交 Result 只是 submitted, 只有独立验证人
--    (verified_by ≠ submitted_by) 置 verified, 才表示 AEOS 接受该结果。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_agent_result (
    result_id         VARCHAR(64) PRIMARY KEY,
    action_id         VARCHAR(64) NOT NULL,
    object_type       VARCHAR(32),
    object_id         VARCHAR(64),
    expected_result   JSONB,
    actual_result     JSONB NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'submitted',  -- submitted|verified|rejected|superseded
    submitted_by      VARCHAR(64) NOT NULL,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_by       VARCHAR(64),
    verified_at       TIMESTAMPTZ,
    verification_note VARCHAR(512),
    evidence_refs     JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_result_action ON plm_agent_result(action_id);
CREATE INDEX IF NOT EXISTS idx_agent_result_status ON plm_agent_result(status);
COMMENT ON TABLE plm_agent_result IS 'DEH 结果(submitted 仅表示执行者声明完成; verified 才表示被接受)';

-- =============================================================================
-- 5. Learning 闭环 (架构 §5.7 / F16)
--    发布门槛: 独立验证(verified_by 非空且 ≠ 产出人) + 可版本化回退(rollback_ref)。
--    status: draft|published|rolled_back|rejected —— rolled_back 保留记录, 不删除。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_learning (
    learning_id  VARCHAR(64) PRIMARY KEY,
    source       VARCHAR(128) NOT NULL,
    content      TEXT NOT NULL,
    version      VARCHAR(16) NOT NULL DEFAULT 'V1',
    status       VARCHAR(16) NOT NULL DEFAULT 'draft',  -- draft|published|rolled_back|rejected
    verified_by  VARCHAR(64),
    verified_at  TIMESTAMPTZ,
    rollback_ref VARCHAR(64),
    created_by   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_learning_status ON plm_learning(status);
COMMENT ON TABLE plm_learning IS 'DEH 学习沉淀(独立验证 + 可版本化回退方可发布)';

-- =============================================================================
-- 6. 证据 subject_type 取值集补齐 (F09 穿透样例)
--    V0.2 §3.4 穿透样例首段与末段的被证明对象是「指标对象」(subject_type=metric),
--    原有取值集只含 object|node|decision|action|result, 指标证据无法登记,
--    样例第 1/6 段会因类型校验被拒 —— 穿透链在第一段就断。
-- =============================================================================
COMMENT ON COLUMN plm_evidence.subject_type IS
    '被证明对象类型: object|node|decision|action|result|metric|ecn|bom|release';

-- =============================================================================
-- 7. 种子: 五类对象唯一 Owner (F01, 角色位; 姓名 G0/RD-G0-007 补录)
-- =============================================================================
INSERT INTO plm_object_owner (object_type, owner_role, effective_date, status) VALUES
('PART',    '业务与产品负责人', DATE '2026-09-24', 'ACTIVE'),
('DRAWING', '业务与产品负责人', DATE '2026-09-24', 'ACTIVE'),
('BOM',     '架构与数据负责人', DATE '2026-09-24', 'ACTIVE'),
('ECO',     '业务与产品负责人', DATE '2026-09-24', 'ACTIVE'),
('RELEASE', '业务与产品负责人', DATE '2026-09-24', 'ACTIVE')
ON CONFLICT (object_type) DO NOTHING;

-- =============================================================================
-- 8. 种子: 穿透样例的 6 段证据落点 (F09 / 架构 V0.2 §3.4)
--    样例链: METRIC-001 → DEC-2026-009 → ACT-2026-014 → RES-2026-006
--            → VER-2026-003 → plm_project_change_log.CHG-2026-0097
--    仅登记证据元数据(subject/source_record_id), 不编造决策/动作/结果的业务内容 ——
--    后三者的业务行由实际运行产生, 样例本身只固定「落点必须存在且非空」。
-- =============================================================================
INSERT INTO plm_evidence
(evidence_id, subject_type, subject_id, evidence_type, source_system, source_record_id, uri, checksum, version, confidence, submitted_by)
VALUES
('E-001', 'metric',   'METRIC-001',   'metric', 'PLM2', 'plm_metric_object.METRIC-001', 'plm_metric_object/METRIC-001', NULL, 'V1', 1.00, 'system'),
('E-002', 'decision', 'DEC-2026-009', 'record', 'PLM2', 'DEC-2026-009',                 NULL, NULL, 'V1', 1.00, 'system'),
('E-003', 'action',   'ACT-2026-014', 'record', 'PLM2', 'ACT-2026-014',                 NULL, NULL, 'V1', 1.00, 'system'),
('E-004', 'result',   'RES-2026-006', 'log',    'PLM2', 'commit:a1b2c3d',               NULL, NULL, 'V1', 1.00, 'system'),
('E-005', 'result',   'RES-2026-006', 'record', 'PLM2', 'VER-2026-003',                 NULL, NULL, 'V1', 1.00, 'system'),
('E-006', 'metric',   'METRIC-001',   'log',    'PLM2', 'plm_project_change_log.CHG-2026-0097', NULL, NULL, 'V1', 1.00, 'system')
ON CONFLICT (evidence_id) DO NOTHING;

-- =============================================================================
-- Done.
-- =============================================================================
