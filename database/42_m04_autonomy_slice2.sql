-- =============================================================================
-- HJ-PLM 增量 33: M04 研发自治中心 — 切片2 自治闭环层
-- 依据 AEOS: contracts/DEH_CONTRACT_V1.md (Evidence/Event 契约)
--            architecture/DEH_BOUNDARY_V1.md (Fact→Event→Decision→Action→Result→Verify→Evidence)
--            architecture/SYSTEM_REGISTRY_V1.md (禁止跨域写表 / 无测试证据不得 Done)
--            contracts/CONTRACT_PACK_V1.md §3 Event / §5 Evidence / §6 Decision-Action
-- 覆盖功能点: F05 跨域契约(八字段信封) / F07 节点双审批 / F08 统一 Gate
--             F09 证据底座 / F10 指标口径 / F13 指标对象治理 / F14 数据产品
-- 说明: 本脚本幂等(IF NOT EXISTS / ON CONFLICT DO NOTHING), 可重复执行。
-- =============================================================================

-- =============================================================================
-- 1. Evidence 证据底座 (F09 / ISS-005)
--    AEOS Evidence 契约四要素: source / version / checksum / confidence
--    穿透链: 管理结果 → 决策 → 动作 → 结果 → 原始记录(subject_type/subject_id + source_record_id)
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_evidence (
    evidence_id      VARCHAR(64) PRIMARY KEY,
    subject_type     VARCHAR(32) NOT NULL,   -- decision|action|result|object|node|ecn|bom|release
    subject_id       VARCHAR(64) NOT NULL,
    evidence_type    VARCHAR(32) NOT NULL,   -- file|image|record|metric|log|approval|external_ref
    source_system    VARCHAR(16) NOT NULL DEFAULT 'PLM2',
    source_record_id VARCHAR(64),            -- 原始记录主键(plm_file.id / node_evidence.id / ...)
    uri              VARCHAR(512),
    checksum         VARCHAR(128),
    version          VARCHAR(32),
    confidence       NUMERIC(3,2),
    provenance       JSONB,
    submitted_by     VARCHAR(64),
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata         JSONB,
    project_no       VARCHAR(64),
    legacy_ref_type  VARCHAR(32),            -- 迁移期: 指向旧证据表名
    legacy_ref_id    VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_evidence_subject ON plm_evidence(subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_evidence_project ON plm_evidence(project_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_evidence_legacy ON plm_evidence(legacy_ref_type, legacy_ref_id)
    WHERE legacy_ref_type IS NOT NULL;
COMMENT ON TABLE plm_evidence IS 'AEOS 统一证据底座: 四要素 source/version/checksum/confidence + 穿透链';
COMMENT ON COLUMN plm_evidence.source_record_id IS '穿透链末端: 指向原始记录(文件/日志/审批)';

-- =============================================================================
-- 2. 统一 Gate 引擎 (F08 / ISS-004)
--    Node 放行与 G0-G8 阶段门共用同一规则模型与同一判定入口, 不存在第二套平行判断。
--    判定结果四态: PENDING / PASS / FAIL / UNKNOWN —— UNKNOWN(输入缺失/未配置规则)
--    不得视为 PASS。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_gate (
    id          BIGSERIAL PRIMARY KEY,
    gate_code   VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    seq         INT NOT NULL DEFAULT 0,
    owner_role  VARCHAR(64),
    version     VARCHAR(16) NOT NULL DEFAULT 'V1',
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_gate IS '统一 Gate 定义(Node 与 G0-G8 阶段门同表)';

CREATE TABLE IF NOT EXISTS plm_gate_rule (
    id         BIGSERIAL PRIMARY KEY,
    gate_id    BIGINT NOT NULL REFERENCES plm_gate(id) ON DELETE CASCADE,
    rule_type  VARCHAR(16) NOT NULL,   -- NODE | APPROVAL | EVIDENCE | METRIC | CONTRACT
    rule_key   VARCHAR(128),
    predicate  JSONB NOT NULL DEFAULT '{}'::jsonb,
    severity   VARCHAR(16) NOT NULL DEFAULT 'BLOCK',  -- BLOCK | WARN
    enabled    BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_gate_rule_gate ON plm_gate_rule(gate_id, enabled);
COMMENT ON TABLE plm_gate_rule IS 'Gate 规则: NODE(节点集)/APPROVAL(审批)/EVIDENCE(证据)/METRIC(指标)/CONTRACT(契约)';

CREATE TABLE IF NOT EXISTS plm_gate_instance (
    id            BIGSERIAL PRIMARY KEY,
    gate_code     VARCHAR(64) NOT NULL,
    object_type   VARCHAR(32) NOT NULL,
    object_id     VARCHAR(64) NOT NULL,
    status        VARCHAR(16) NOT NULL,   -- PENDING | PASS | FAIL | UNKNOWN
    failed_rules  JSONB,
    evidence_refs JSONB,
    reason        VARCHAR(512),
    evaluated_by  VARCHAR(64),
    evaluated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gate_instance_obj ON plm_gate_instance(gate_code, object_type, object_id);
COMMENT ON TABLE plm_gate_instance IS 'Gate 判定实例(每次判定留痕, 唯一放行判定证据)';

-- =============================================================================
-- 3. 节点双审批 (F07 / ISS-001)
--    7 个关键节点: 研发主管(L1) → 总经理(L2)。审批表保留历史(不做唯一约束),
--    当前有效审批 = 每级最新一条; REVOKED 表示撤回后需重新审批(支持 驳回/回退/重开)。
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_project_node_approval (
    id          BIGSERIAL PRIMARY KEY,
    node_id     BIGINT NOT NULL REFERENCES plm_project_node(id) ON DELETE CASCADE,
    project_id  BIGINT,
    project_no  VARCHAR(64),
    node_code   VARCHAR(32) NOT NULL,
    level       SMALLINT NOT NULL,          -- 1=研发主管 RD_LEAD, 2=总经理 GM
    role_code   VARCHAR(32) NOT NULL,
    approver    VARCHAR(64) NOT NULL,
    decision    VARCHAR(16) NOT NULL,       -- APPROVED | REJECTED | REVOKED
    comment     VARCHAR(512),
    decided_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_node_approval_node ON plm_project_node_approval(node_id, level, decided_at DESC);
COMMENT ON TABLE plm_project_node_approval IS '关键节点双级审批记录(研发主管→总经理), 保留历史';

-- =============================================================================
-- 4. 指标对象治理 (F13 / ISS-009) + 数据产品注册 (F14 / ISS-010)
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_metric_object (
    metric_code         VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    business_definition TEXT NOT NULL,
    owner_role          VARCHAR(64) NOT NULL,   -- Accountable Business Owner(角色位, 姓名 G0 补录)
    owner_id            VARCHAR(64),
    version             VARCHAR(16) NOT NULL DEFAULT 'V1',
    valid_from          DATE,
    valid_to            DATE,
    quality_rule        JSONB,
    baseline_method     TEXT,                   -- 无历史基线时: 采集方法(不编造数值)
    threshold           JSONB,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_metric_object IS '研发指标对象(Owner/定义/版本/有效期/质量规则)';

CREATE TABLE IF NOT EXISTS plm_data_product (
    product_code         VARCHAR(64) PRIMARY KEY,
    name                 VARCHAR(128) NOT NULL,
    domain               VARCHAR(64) NOT NULL,
    owner_role           VARCHAR(64) NOT NULL,
    owner_id             VARCHAR(64),
    authoritative_source TEXT NOT NULL,          -- 唯一真相源(不得引入第二事实源)
    service_contract     JSONB NOT NULL,         -- 含 version + source/version/evidence_refs
    sla                  JSONB,
    permission           JSONB,
    status               VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_data_product IS '研发域数据产品注册(7 个 x360)';

-- =============================================================================
-- 5. 事件八字段信封 (F05 / CONTRACT_PACK_V1 §3)
--    AEOS 要求: event_id/event_type/object_ref/occurred_at/source_system/
--                correlation_id/version/evidence_refs
--    增量加列, 保留 aggregate_type/aggregate_id 兼容旧消费方。
--    注: source_system / revision 由增量 26 建立, 此处 IF NOT EXISTS 幂等重申。
-- =============================================================================
ALTER TABLE plm_domain_event
    ADD COLUMN IF NOT EXISTS object_type     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS object_id       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS source_system   VARCHAR(16),
    ADD COLUMN IF NOT EXISTS version         INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS evidence_refs   JSONB;

-- correlation_id: 增量 26 把它建成了 UUID, 但 AEOS 信封契约(增量 32)在九张工作表上
-- 用的是 VARCHAR(64)。同一信封字段存在两种类型时, 调用方传入非 UUID 关联号
-- (如 "ECN-2026-001") 会被 uuid 输入函数拒绝, 事件写库失败并被 catch 吞掉 ——
-- 表现为「事件静默丢失」, 恰是 F05 要消灭的问题。统一为契约类型。uuid → text 无损。
ALTER TABLE plm_domain_event
    ALTER COLUMN correlation_id TYPE VARCHAR(64) USING correlation_id::text;

UPDATE plm_domain_event SET object_type = aggregate_type WHERE object_type IS NULL AND aggregate_type IS NOT NULL;
UPDATE plm_domain_event SET object_id   = aggregate_id   WHERE object_id   IS NULL AND aggregate_id   IS NOT NULL;
UPDATE plm_domain_event SET occurred_at = created_at     WHERE occurred_at IS NULL;
UPDATE plm_domain_event SET source_system = 'PLM2'       WHERE source_system IS NULL;
UPDATE plm_domain_event SET version = 0                  WHERE version IS NULL;
CREATE INDEX IF NOT EXISTS idx_domain_event_corr ON plm_domain_event(correlation_id);
CREATE INDEX IF NOT EXISTS idx_domain_event_obj  ON plm_domain_event(object_type, object_id);
COMMENT ON COLUMN plm_domain_event.object_type IS 'AEOS 对象引用 object_ref.object_type';
COMMENT ON COLUMN plm_domain_event.object_id   IS 'AEOS 对象引用 object_ref.object_id';

-- =============================================================================
-- 6. 生命周期: 非法跳转留痕 + 守卫/副作用 (F04)
--    现状: 非法跳转抛 409 但不留痕 → 无法满足「连续 3 次非法跳转 → 3 条失败记录」。
-- =============================================================================
ALTER TABLE plm_lifecycle_transition
    ADD COLUMN IF NOT EXISTS guards       JSONB,
    ADD COLUMN IF NOT EXISTS side_effects JSONB;

ALTER TABLE plm_lifecycle_history
    ADD COLUMN IF NOT EXISTS result VARCHAR(16) NOT NULL DEFAULT 'OK';  -- OK | DENIED
CREATE INDEX IF NOT EXISTS idx_lc_hist_result ON plm_lifecycle_history(object_type, object_id, result);
COMMENT ON COLUMN plm_lifecycle_history.result IS 'OK=成功迁移; DENIED=非法跳转被拒(留痕证据)';

-- 被拒绝的迁移没有目标状态、也可能拿不到对象标识(旧调用方只传对象类型),
-- 原 NOT NULL 约束是按「只记成功迁移」写的。放开这两列, 拒绝记录才落得下去 ——
-- 否则 DENIED 留痕会被约束静默丢弃, 又回到「非法跳转查无此事」。
ALTER TABLE plm_lifecycle_history ALTER COLUMN to_state  DROP NOT NULL;
ALTER TABLE plm_lifecycle_history ALTER COLUMN object_id DROP NOT NULL;

-- =============================================================================
-- 7. 种子: Gate 定义与规则
-- =============================================================================
INSERT INTO plm_gate (gate_code, name, seq, owner_role, version) VALUES
('GATE_NODE_KEY_DONE',    '关键节点完成放行门', 10, '研发主管',   'V1'),
('GATE_TECH_TRANSFER',    '技转齐套门',         20, '技转负责人', 'V1'),
('GATE_G0', 'G0 立项与价值评定', 0, '项目决策者', 'V1'),
('GATE_G1', 'G1 流程与需求确认', 1, '业务与产品负责人', 'V1'),
('GATE_G2', 'G2 架构与开发准备', 2, '架构与数据负责人', 'V1'),
('GATE_G3', 'G3 开发联调',       3, '架构与数据负责人', 'V1'),
('GATE_G4', 'G4 独立测试与业务验收', 4, '验收人', 'V1'),
('GATE_G5', 'G5 发布与交接',     5, '发布负责人', 'V1'),
('GATE_G6', 'G6 真实运行评价',   6, '业务与产品负责人', 'V1'),
('GATE_G7', 'G7 复制与推广',     7, '业务与产品负责人', 'V1'),
('GATE_G8', 'G8 闭环与学习',     8, '业务与产品负责人', 'V1')
ON CONFLICT (gate_code) DO NOTHING;

-- 关键节点放行门规则: 审批(双级) + 证据(>=1) + 节点(实际完成日期)
INSERT INTO plm_gate_rule (gate_id, rule_type, rule_key, predicate, severity)
SELECT g.id, r.rule_type, r.rule_key, r.predicate::jsonb, 'BLOCK'
FROM plm_gate g
JOIN (VALUES
    ('APPROVAL', 'DUAL_LEVEL',    '{"minLevels":2,"levels":[{"level":1,"roleCode":"RD_LEAD"},{"level":2,"roleCode":"GM"}]}'),
    ('EVIDENCE', 'NODE_EVIDENCE', '{"minCount":1}'),
    ('NODE',     'ACTUAL_DATE',   '{"requireActualDate":true}')
) AS r(rule_type, rule_key, predicate) ON TRUE
WHERE g.gate_code = 'GATE_NODE_KEY_DONE'
  AND NOT EXISTS (SELECT 1 FROM plm_gate_rule x WHERE x.gate_id = g.id AND x.rule_type = r.rule_type AND x.rule_key = r.rule_key);

-- 技转齐套门规则: 受控版本/BOM/图纸/SOP/检验/测试证据 六项齐备(PAND-71 F19)
INSERT INTO plm_gate_rule (gate_id, rule_type, rule_key, predicate, severity)
SELECT g.id, 'CONTRACT', 'TECH_TRANSFER_KIT',
       ('{"required":["controlled_version","bom","drawing","sop","inspection","test_evidence"]}')::jsonb, 'BLOCK'
FROM plm_gate g
WHERE g.gate_code = 'GATE_TECH_TRANSFER'
  AND NOT EXISTS (SELECT 1 FROM plm_gate_rule x WHERE x.gate_id = g.id AND x.rule_type = 'CONTRACT');

-- G0-G8 阶段门规则: 由 G1「Node 与 G0-G8 映射」任务填充, 现为空 →
-- 判定返回 UNKNOWN(不得视为 PASS), 符合「未配置规则不得放行」。

-- =============================================================================
-- 8. 种子: 14 个研发指标对象 (F13)
--    threshold 为空 = 无历史基线, 先定义采集方法, 阈值 G1/G2 评审前固定 (不编造数值)。
--    owner_role 为角色位, 真实姓名 G0 补录 (PAND-51 待澄清 2)。
-- =============================================================================
INSERT INTO plm_metric_object
(metric_code, name, business_definition, owner_role, version, quality_rule, baseline_method, threshold, status) VALUES
('M_DQ_SCORE',      '主数据质量均分',   '当前全库主数据质量规则均分(DQ Score), 反映主数据可交付程度', '架构与数据负责人', 'V1', '{"min":0,"max":100}', '每日批计算全库 DQ 均分入库', NULL, 'ACTIVE'),
('M_CODE_AUTO_RATE','自动编号率',       '近 30 天新建料号中由系统自动编号规则生成的比例',           '架构与数据负责人', 'V1', '{"min":0,"max":1}',   '按创建时间滚动 30 天窗口统计', NULL, 'ACTIVE'),
('M_ECN_CYCLE_H',   'ECN 平均闭环小时','近 N 天生效 ECN 从发起到 EFFECTIVE 的平均小时数',          '架构与数据负责人', 'V1', '{"min":0}',           '按 ECN 生效事件时间戳差值统计', NULL, 'ACTIVE'),
('M_WIP_DRAFT',     '超龄草稿料号数',   '处于 DRAFT/REVIEWING 且超过约定天数的料号数量',            '业务与产品负责人', 'V1', '{"min":0}',           '按状态+停留时长快照统计', NULL, 'ACTIVE'),
('M_BOM_REWORK_7D', '发布后7日回退次数','BOM 发布(RELEASED)后 7 日内回退到 CHANGING 的次数',        '业务与产品负责人', 'V1', '{"min":0}',           '按 BOM 生命周期历史迁移时间差统计', NULL, 'ACTIVE'),
('M_DOC_COMPLETE',  '技转一次齐套率',   '已发布且无阻断项且 DQ score>=90 的文档占比',               '业务与产品负责人', 'V1', '{"min":0,"max":1}',   '按技转齐套检查结果统计', NULL, 'ACTIVE'),
('M_AGENT_ADOPT',   'Agent 建议采纳率','Agent 建议被人类批准(APPROVED)的比例',                     '业务与产品负责人', 'V1', '{"min":0,"max":1}',   '按 plm_agent_action 决策结果统计', NULL, 'ACTIVE'),
('M_IMPROVE_CLOSE', '改善闭环率',       '改善洞察从 NEW 到已关闭的闭环比例',                        '业务与产品负责人', 'V1', '{"min":0,"max":1}',   '按改善单状态流转统计', NULL, 'ACTIVE'),
('M_CBOM_SYNC',     'CBOM 同步率',      'CBOM 与结构 BOM 成本数据一致的比例',                       '架构与数据负责人', 'V1', '{"min":0,"max":1}',   '按对账差异计数统计', NULL, 'ACTIVE'),
('M_LOCK_VER',      '版本锁定合规率',   '已发布料号版本锁定记录齐备的比例',                         '架构与数据负责人', 'V1', '{"min":0,"max":1}',   '按锁定记录覆盖统计', NULL, 'ACTIVE'),
('M_OUTSRC_LEAK',   '外协泄密风险数',   '外协场景中文件权限越界的次数',                             '架构与数据负责人', 'V1', '{"min":0}',           '按文件访问审计异常计数', NULL, 'ACTIVE'),
('M_GATE_OTD',      'Gate 准时放行率',  '阶段门在计划期内完成放行的比例',                           '项目决策者',       'V1', '{"min":0,"max":1}',   '按 Gate 实例放行时间与计划日期比对', NULL, 'ACTIVE'),
('M_PROJECT_HEALTH','项目健康分',       '项目进度健康分 = 0.35*节点完成率 + 0.25*关键节点完成率 + 0.20*准时率 + 0.10*必填字段完整率 + 0.10*变更因子; 口径详见 com.hjgd.plm.metric.domain.HealthScoreDef', '业务与产品负责人', 'V1', '{"min":0,"max":100}', '按项目节点与变更留痕逐项目计算, 见 HealthScoreDef.COLLECTION_METHOD', NULL, 'ACTIVE'),
('M_REQ_VERIFY_COVERAGE', '需求验证覆盖率', '被验证通过的需求数 / 需求总数(排除项见 F15 口径定义)',  '业务与产品负责人', 'V1', '{"min":0,"max":1}',   '按 需求→测试计划→测试结果→证据 追溯链统计', NULL, 'ACTIVE')
ON CONFLICT (metric_code) DO NOTHING;

-- =============================================================================
-- 9. 种子: 7 个 x360 数据产品 (F14)
--    authoritative_source 必须为唯一真相源, 分析数据集不得冒充正式数据产品。
-- =============================================================================
INSERT INTO plm_data_product
(product_code, name, domain, owner_role, authoritative_source, service_contract, sla, permission, status) VALUES
('Project360',   '项目360',   '项目管理', '业务与产品负责人',
 'PLM2 plm_project / plm_project_node (唯一)', '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN","ENGINEER"]}', 'ACTIVE'),
('Product360',   '产品360',   '产品主数据', '架构与数据负责人',
 'PLM2 plm_material (唯一)',                  '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN","ENGINEER"]}', 'ACTIVE'),
('BOM360',       'BOM360',    '结构数据', '架构与数据负责人',
 'PLM2 plm_bom / plm_bom_item (唯一)',        '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN","ENGINEER"]}', 'ACTIVE'),
('Validation360', '验证360',  '验证管理', '业务与产品负责人',
 'PLM2 验证/测试对象 (切片3 落地, 现无第二事实源)', '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN"]}', 'ACTIVE'),
('Change360',    '变更360',   '变更管理', '架构与数据负责人',
 'PLM2 plm_ecn + plm_project_change_log (唯一)', '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN"]}', 'ACTIVE'),
('Trial360',     '试产360',   '试产管理', '业务与产品负责人',
 'PLM2 plm_trial_report (唯一)',              '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN"]}', 'ACTIVE'),
('Evidence360',  '证据360',   '证据管理', '架构与数据负责人',
 'PLM2 plm_evidence (唯一, 切片2 新建)',      '{"version":"V1","returns":["source","version","evidence_refs"]}', '{"freshness":"T+1","availability":"99%"}', '{"roles":["RD_LEAD","ADMIN"]}', 'ACTIVE')
ON CONFLICT (product_code) DO NOTHING;

-- =============================================================================
-- Done.
-- =============================================================================
