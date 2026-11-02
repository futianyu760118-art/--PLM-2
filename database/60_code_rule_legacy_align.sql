-- =============================================================================
-- 60_code_rule_legacy_align.sql
-- 编码规则与老 PLM 对齐（M04 关键任务）
--
-- 背景（线上实测 2026-10-31）：
--   现网物料/项目编号仍是老 PLM 格式，例：HJ.26044.R1.1008 / HJ.26042.M1.000 / HJ.26035.V1.000
--   新系统 CodeGenServiceImpl 实际只读 sys_sequence（HJ + yyyyMMdd + 序号），
--   例：HJ202610300011 —— 仅 1 条项目在用，其余 162 个项目全是老格式。
--   => 两套编号体系并存，新增物料/项目会与老体系脱节。
--
-- 本脚本做三件事：
--   1) 补 sys_sequence 缺失的序列（MOLD_NO 被 SEQ_MAP 引用但无种子，调用即失败）
--   2) 把老 PLM 的 6 条编码规则 + 9 条序列登记为 DRAFT 基线（pattern 待业务核对后填入）
--   3) 新增 M04 需求/风险/文档/ECR 四条规则骨架（DRAFT，不生效）
--
-- 注意：本脚本只登记，不改写 CodeGenServiceImpl 行为。
--       规则生效需完成代码改造（读 plm_code_rule + plm_code_rule_segment）后，
--       把 status 置为 'ENABLED' 并切换调用路径。详见《M04 核心建设任务包》。
-- =============================================================================

-- ---------- 1. 补齐被引用但缺失的序列（当前会直接抛异常的项）----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator) VALUES
    ('MOLD_NO',    'MOLD', 'yyyyMMdd', 4, ''),   -- CodeGenServiceImpl.SEQ_MAP 已引用，原无种子
    ('PROJECT_NO', 'HJ',   'yyyyMMdd', 4, ''),   -- 新体系项目号（老项目号不走此序列）
    ('TASK_NO',    'TASK', 'yyyyMMdd', 4, ''),
    ('REQ_NO',     'REQ',  'yyyyMMdd', 4, ''),
    ('RISK_NO',    'RISK', 'yyyyMMdd', 4, ''),
    ('DOC_NO',     'DOC',  'yyyyMMdd', 4, ''),
    ('ECR_NO',     'ECR',  'yyyyMMdd', 4, '')    -- 原 SEQ_MAP 把 ECR 错误映射到 ECN_NO
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 2. 老 PLM 编码规则基线登记（原文来自老系统「系统设置-编码规则」实测）----------
-- 来源页面：http://192.168.0.43/PLM/Settings/CodeRuleBrowse.aspx
-- 老系统规则清单（实测 6 条）：
--   001 项目编码   编码类型=项目
--   002 任务编码   编码类型=任务
--   003 项目流程   编码类型=流程表单
--   004 流程编码   编码类型=流程表单
--   005 工程变更   编码类型=设计变更
--   006 表单编码规则 编码类型=业务表单
INSERT INTO plm_code_rule (rule_code, object_type, name, version_no, status, pattern, seq_key,
                           reset_policy, uniqueness_scope, allow_manual_override, override_roles)
VALUES
    ('LEGACY_PROJECT',      'PROJECT',      '老PLM-001 项目编码', 'V1', 'DRAFT',
     'HJ.{yy}{projSeq:3}.{typeCode}.{itemSeq:3}', 'PROJECT_NO', 'YEARLY', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('LEGACY_TASK',         'TASK',         '老PLM-002 任务编码', 'V1', 'DRAFT',
     'TBD-需读取老系统规则详情', 'TASK_NO', 'NEVER', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('LEGACY_WF_PROJECT',   'WORKFLOW',     '老PLM-003 项目流程编码', 'V1', 'DRAFT',
     'TBD-需读取老系统规则详情', 'PROJECT_NO', 'NEVER', 'GLOBAL', FALSE, NULL),
    ('LEGACY_WF',           'WORKFLOW',     '老PLM-004 流程编码', 'V1', 'DRAFT',
     'TBD-需读取老系统规则详情', 'TASK_NO', 'NEVER', 'GLOBAL', FALSE, NULL),
    ('LEGACY_CHANGE',       'ECN',          '老PLM-005 工程变更编码', 'V1', 'DRAFT',
     'TBD-需读取老系统规则详情', 'ECN_NO', 'NEVER', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('LEGACY_FORM',         'FORM',         '老PLM-006 表单编码规则', 'V1', 'DRAFT',
     'TBD-需读取老系统规则详情', 'DOC_NO', 'NEVER', 'GLOBAL', FALSE, NULL)
ON CONFLICT (rule_code) DO NOTHING;

-- ---------- 3. M04 新增对象规则骨架（DRAFT，不生效）----------
INSERT INTO plm_code_rule (rule_code, object_type, name, version_no, status, pattern, seq_key,
                           reset_policy, uniqueness_scope, allow_manual_override, override_roles)
VALUES
    ('M04_REQ',  'REQUIREMENT', '需求编号',  'V1', 'DRAFT', 'REQ{yyyy}{seq:4}',  'REQ_NO',  'YEARLY', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('M04_RISK', 'RISK',        '风险编号',  'V1', 'DRAFT', 'RISK{yyyy}{seq:4}', 'RISK_NO', 'YEARLY', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('M04_DOC',  'DOCUMENT',    '文档编号',  'V1', 'DRAFT', 'DOC{yyyy}{seq:4}',  'DOC_NO',  'YEARLY', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD'),
    ('M04_ECR',  'ECR',         '变更申请编号','V1','DRAFT', 'ECR{yyyy}{seq:4}',  'ECR_NO',  'NEVER',  'GLOBAL', FALSE, NULL)
ON CONFLICT (rule_code) DO NOTHING;

-- 上面 M04_* 三条 pattern 为占位建议值，需研发负责人确认编号前缀后修改。

-- ---------- 4. 老 PLM 序列库登记（原文来自「系统设置-序列管理」，实测 9 条）----------
-- 老系统序列：cp两位数、cp一位数、大写字母、两位数、三位数、小版本(0-5)、
--             小写字母、一位数(V1-V9)、一位自然数
-- 这些是编码规则分段的可选值域，新系统对应 plm_code_rule_segment.dict_type / value_expr。
-- 此处登记到 sys_dict 便于页面下拉选择（dict_type 统一前缀 LEGACY_SEQ_）。
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT v.dict_type, v.dict_value, v.dict_label, v.sort_order, 1
FROM (VALUES
    ('LEGACY_SEQ_CP2',    'CP2',   'cp两位数 01-71',        1),
    ('LEGACY_SEQ_CP1',    'CP1',   'cp一位数 V0-V9',        2),
    ('LEGACY_SEQ_ALPHA_U','AU',    '大写字母 A-Z',           3),
    ('LEGACY_SEQ_NUM2',   'N2',    '两位数 00-71',           4),
    ('LEGACY_SEQ_NUM3',   'N3',    '三位数 001-010',         5),
    ('LEGACY_SEQ_SUBVER', 'SUB',   '小版本 0-5',             6),
    ('LEGACY_SEQ_ALPHA_L','AL',    '小写字母 a-z',           7),
    ('LEGACY_SEQ_V1',     'V1',    '一位数 V1-V9',           8),
    ('LEGACY_SEQ_NAT1',   'N1',    '一位自然数 1-9',         9)
) AS v(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value
);

-- ---------- 5. 迁移追溯初始化：把现网 40 条物料登记为老编号对照 ----------
-- 说明：现网 40 条物料编号形如 HJ.26044.R1.1008，均来自老 PLM。
--       这里只建映射骨架，legacy_id 需在老库取数后回填；不可在当前环境凭空生成。
-- INSERT INTO plm_legacy_map (source_system, object_type, legacy_id, legacy_code, new_code, migrate_status)
-- SELECT 'OLD_PLM', 'MATERIAL', m.part_no, m.part_no, m.part_no, 'DONE'
-- FROM plm_material m
-- WHERE m.part_no LIKE 'HJ.%'
-- ON CONFLICT DO NOTHING;

-- =============================================================================
-- 待业务确认（本脚本刻意留空，不猜）：
--   1. 老编号 HJ.26044.R1.1008 的 R1/M1/V1 段含义（产品线？部门？物料类别？）
--   2. 第 4 段序号位数规则（1008 四位 / 689 三位 / 000 表示主件？）
--   3. 项目号后缀 -1 / JY 的生成规则与去重口径
--   4. 老系统 6 条规则的 pattern 详情（需在 CodeRuleBrowse 逐条打开「修改」查看）
-- =============================================================================
