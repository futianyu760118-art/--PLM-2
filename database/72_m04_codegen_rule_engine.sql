-- =============================================================================
-- HJ-PLM 增量 64: 编码规则引擎接入（T01 + T02）
-- 目标：
--   1) 让 CodeGenServiceImpl 真正读 plm_code_rule + plm_code_rule_segment，
--      改规则不需要发版（这是 T01 的验收标准）
--   2) 修 T02 三个 bug：ECR 错映射 ECN_NO / MOLD_NO 无序列 / 未知类型报错不明确
--   3) 为现有 12 个 seq_key 建 ACTIVE 规则，输出与改造前**逐字节一致**
--      （改造是"接管"，不是"改格式"，避免上线瞬间历史编号规则突变）
--
-- 执行顺序：本脚本必须在后端发布**之前**执行
--   sys_sequence.last_period 列被 SequenceService.nextValue() 引用
--
-- 幂等：全部 ON CONFLICT DO NOTHING / IF NOT EXISTS，可重复执行
-- =============================================================================

BEGIN;

-- ---------- 1. sys_sequence 支持按期间重置 ----------
ALTER TABLE sys_sequence ADD COLUMN IF NOT EXISTS last_period VARCHAR(16);
COMMENT ON COLUMN sys_sequence.last_period IS '上次取号所属期间(YEAR=yyyy/MONTH=yyyyMM/DAY=yyyyMMdd/NEVER=空)，用于 reset_policy 判断是否归零';

-- ---------- 2. plm_code_rule 补执行期字段 ----------
ALTER TABLE plm_code_rule ADD COLUMN IF NOT EXISTS sample_code VARCHAR(128);
COMMENT ON COLUMN plm_code_rule.sample_code IS '示例编号，仅用于页面展示';
ALTER TABLE plm_code_rule ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 100;
COMMENT ON COLUMN plm_code_rule.priority IS '同一 object_type 多条生效规则时，数字小者优先';

-- ---------- 3. 为 12 个在用 seq_key 建 ACTIVE 规则（输出与现状一致）----------
-- 现状格式：prefix + yyyyMMdd + 补零(length) 例 PART_NO -> HJ202610300001
-- 规则化后：PREFIX + DATE(yyyyMMdd) + SEQ(length) 完全等价
-- pattern / sample_code 按线上真实前缀填写（EX / IQ / MO / LX / OS / PR / WI / INS）
INSERT INTO plm_code_rule (rule_code, object_type, name, version_no, status, pattern,
                           seq_key, reset_policy, uniqueness_scope, preview_enabled,
                           allow_manual_override, sample_code, priority)
VALUES
    ('PART_NO_V1',      'PART',        '料号',           'V1', 'ACTIVE', 'HJ+yyyyMMdd+0000',   'PART_NO',       'NEVER', 'GLOBAL', TRUE, TRUE,  'HJ202610300001',   100),
    ('ECN_NO_V1',       'ECN',         'ECN 变更单号',   'V1', 'ACTIVE', 'ECN+yyyyMMdd+0000',  'ECN_NO',        'NEVER', 'GLOBAL', TRUE, FALSE, 'ECN202610300001',  100),
    ('BOM_NO_V1',       'BOM',         'BOM 编号',       'V1', 'ACTIVE', 'BOM+yyyyMMdd+0000',  'BOM_NO',        'NEVER', 'GLOBAL', TRUE, FALSE, 'BOM202610300001',  100),
    ('MOLD_NO_V1',      'MOLD',        '模具编号',       'V1', 'ACTIVE', 'MO+yyyyMMdd+0000',   'MOLD_NO',       'NEVER', 'GLOBAL', TRUE, FALSE, 'MO202610300001',   100),
    ('ECR_NO_V1',       'ECR',         'ECR 变更申请号', 'V1', 'ACTIVE', 'ECR+yyyyMMdd+0000',  'ECR_NO',        'NEVER', 'GLOBAL', TRUE, FALSE, 'ECR202610300001',  100),
    ('ISSUE_NO_V1',     'ISSUE',       '问题编号',       'V1', 'ACTIVE', 'IQ+yyyyMMdd+0000',   'ISSUE_NO',      'NEVER', 'GLOBAL', TRUE, FALSE, 'IQ202610300001',   100),
    ('INSIGHT_NO_V1',   'INSIGHT',     '洞察编号',       'V1', 'ACTIVE', 'INS+yyyyMMdd+0000',  'INSIGHT_NO',    'NEVER', 'GLOBAL', TRUE, FALSE, 'INS202610300001',  100),
    ('EXPORT_NO_V1',    'EXPORT',      '导出任务编号',   'V1', 'ACTIVE', 'EX+yyyyMMdd+0000',   'EXPORT_NO',     'NEVER', 'GLOBAL', TRUE, FALSE, 'EX202610300001',   100),
    ('OUTSOURCE_NO_V1', 'OUTSOURCE',   '委外申请编号',   'V1', 'ACTIVE', 'OS+yyyyMMdd+0000',   'OUTSOURCE_NO',  'NEVER', 'GLOBAL', TRUE, FALSE, 'OS202610300001',   100),
    ('PROCESS_NO_V1',   'PROCESS',     '工艺路线编号',   'V1', 'ACTIVE', 'PR+yyyyMMdd+0000',   'PROCESS_NO',    'NEVER', 'GLOBAL', TRUE, FALSE, 'PR202610300001',   100),
    ('WORK_ITEM_NO_V1', 'WORK_ITEM',   '工序编号',       'V1', 'ACTIVE', 'WI+yyyyMMdd+0000',   'WORK_ITEM',     'NEVER', 'GLOBAL', TRUE, FALSE, 'WI202610300001',   100),
    ('INITIATION_NO_V1','INITIATION',  '立项单号',       'V1', 'ACTIVE', 'LX+yyyyMMdd+0000',   'INITIATION_NO', 'NEVER', 'GLOBAL', TRUE, FALSE, 'LX202610300001',   100)
ON CONFLICT (rule_code) DO NOTHING;

-- ---------- 4. 规则分段明细 ----------
-- segment_type: PREFIX(序列前缀) / CONST(常量) / DATE(日期) / SEQ(序列) / DICT(字典) / FIELD(上下文取值)
-- value_expr:   CONST=原文 / DATE=日期格式 / DICT=取不到时的兜底值 / FIELD=可空
-- pad_length:   仅 SEQ 使用，补零宽度
--
-- 【重要】第 1 段统一用 PREFIX，从 sys_sequence.prefix 实时读取，不写死 CONST。
--   原因：线上实际前缀与直觉不一致 —— EXPORT_NO=EX / ISSUE_NO=IQ / MOLD_NO=MO /
--   INITIATION_NO=LX / OUTSOURCE_NO=OS / PROCESS_NO=PR / WORK_ITEM=WI。
--   写死 CONST 会让上线瞬间编号格式突变，与历史单号脱节。
INSERT INTO plm_code_rule_segment (rule_id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
SELECT r.id, v.seq_no, v.segment_type, v.value_expr, v.pad_length, v.dict_type, v.field_path, v.transform
FROM plm_code_rule r
JOIN (VALUES
    ('PART_NO_V1',      1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('PART_NO_V1',      2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('PART_NO_V1',      3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('ECN_NO_V1',       1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('ECN_NO_V1',       2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('ECN_NO_V1',       3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('BOM_NO_V1',       1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('BOM_NO_V1',       2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('BOM_NO_V1',       3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('MOLD_NO_V1',      1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('MOLD_NO_V1',      2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('MOLD_NO_V1',      3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('ECR_NO_V1',       1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('ECR_NO_V1',       2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('ECR_NO_V1',       3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('ISSUE_NO_V1',     1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('ISSUE_NO_V1',     2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('ISSUE_NO_V1',     3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('INSIGHT_NO_V1',   1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('INSIGHT_NO_V1',   2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('INSIGHT_NO_V1',   3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('EXPORT_NO_V1',    1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('EXPORT_NO_V1',    2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('EXPORT_NO_V1',    3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('OUTSOURCE_NO_V1', 1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('OUTSOURCE_NO_V1', 2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('OUTSOURCE_NO_V1', 3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('PROCESS_NO_V1',   1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('PROCESS_NO_V1',   2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('PROCESS_NO_V1',   3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('WORK_ITEM_NO_V1', 1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('WORK_ITEM_NO_V1', 2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('WORK_ITEM_NO_V1', 3, 'SEQ',   NULL,          4, NULL, NULL, NULL),
    ('INITIATION_NO_V1',1, 'PREFIX', NULL, NULL, NULL, NULL, NULL),
    ('INITIATION_NO_V1',2, 'DATE',  'yyyyMMdd', NULL, NULL, NULL, NULL),
    ('INITIATION_NO_V1',3, 'SEQ',   NULL,          4, NULL, NULL, NULL)
) AS v(rule_code, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
  ON r.rule_code = v.rule_code
WHERE NOT EXISTS (
    SELECT 1 FROM plm_code_rule_segment s WHERE s.rule_id = r.id AND s.seq_no = v.seq_no
);

-- ---------- 5. 校验视图：当前生效规则一览 ----------
CREATE OR REPLACE VIEW v_codegen_rule_effective AS
SELECT
    r.rule_code,
    r.object_type,
    r.name,
    r.version_no,
    r.pattern,
    r.seq_key,
    r.reset_policy,
    r.allow_manual_override,
    r.sample_code,
    r.priority,
    r.effective_from,
    r.effective_to,
    (SELECT COUNT(*) FROM plm_code_rule_segment s WHERE s.rule_id = r.id) AS segment_count,
    (SELECT COUNT(*) FROM plm_code_rule_segment s WHERE s.rule_id = r.id AND s.segment_type = 'SEQ') AS seq_segment_count,
    ARRAY(
        SELECT s.segment_type || COALESCE(':' || s.value_expr, '') || COALESCE('(' || s.pad_length || ')', '')
        FROM plm_code_rule_segment s WHERE s.rule_id = r.id ORDER BY s.seq_no
    ) AS segments
FROM plm_code_rule r
WHERE r.status = 'ACTIVE'
  AND (r.effective_from IS NULL OR r.effective_from <= NOW())
  AND (r.effective_to   IS NULL OR r.effective_to   >= NOW());

COMMENT ON VIEW v_codegen_rule_effective IS '当前生效的编码规则（status=ACTIVE 且在有效期内）';

-- ---------- 6. 自检 ----------
DO $$
DECLARE
    v_rule_cnt   INT;
    v_no_seg     INT;
    v_seq_dup    INT;
BEGIN
    SELECT COUNT(*) INTO v_rule_cnt FROM plm_code_rule WHERE status = 'ACTIVE';
    SELECT COUNT(*) INTO v_no_seg
      FROM plm_code_rule r
     WHERE r.status = 'ACTIVE'
       AND NOT EXISTS (SELECT 1 FROM plm_code_rule_segment s WHERE s.rule_id = r.id);
    -- 每条规则最多 1 个 SEQ 段（多 SEQ 段会让 allocate 语义变复杂，先禁掉）
    SELECT COUNT(*) INTO v_seq_dup FROM (
        SELECT r.id FROM plm_code_rule r
          JOIN plm_code_rule_segment s ON s.rule_id = r.id AND s.segment_type = 'SEQ'
         WHERE r.status = 'ACTIVE'
         GROUP BY r.id HAVING COUNT(*) > 1
    ) t;

    RAISE NOTICE '生效规则数 = %, 无分段规则数 = %, 多SEQ段规则数 = %', v_rule_cnt, v_no_seg, v_seq_dup;
    IF v_no_seg > 0 THEN
        RAISE WARNING '存在 % 条生效规则没有分段配置，allocate 将回落到 sys_sequence 兼容模式', v_no_seg;
    END IF;
    IF v_seq_dup > 0 THEN
        RAISE WARNING '存在 % 条规则配置了多个 SEQ 段，只会使用第一个', v_seq_dup;
    END IF;
END $$;

COMMIT;

-- =============================================================================
-- 重复执行注意
-- =============================================================================
-- 分段用 WHERE NOT EXISTS 保证幂等：已存在的 seq_no 不会被覆盖。
-- 如果你曾执行过本脚本的早期版本（第 1 段是 CONST 而非 PREFIX），需先清分段再重跑：
--   DELETE FROM plm_code_rule_segment
--    WHERE rule_id IN (SELECT id FROM plm_code_rule WHERE rule_code LIKE '%_NO_V1')
--      AND seq_no = 1 AND segment_type = 'CONST';
-- 然后重新执行本脚本。
-- =============================================================================

-- =============================================================================
-- 回滚（如需）
-- =============================================================================
-- BEGIN;
-- DELETE FROM plm_code_rule WHERE rule_code IN (
--   'PART_NO_V1','ECN_NO_V1','BOM_NO_V1','MOLD_NO_V1','ECR_NO_V1','ISSUE_NO_V1',
--   'INSIGHT_NO_V1','EXPORT_NO_V1','OUTSOURCE_NO_V1','PROCESS_NO_V1',
--   'WORK_ITEM_NO_V1','INITIATION_NO_V1');
-- DROP VIEW IF EXISTS v_codegen_rule_effective;
-- ALTER TABLE plm_code_rule DROP COLUMN IF EXISTS sample_code;
-- ALTER TABLE plm_code_rule DROP COLUMN IF EXISTS priority;
-- ALTER TABLE sys_sequence DROP COLUMN IF EXISTS last_period;
-- COMMIT;
-- =============================================================================
-- Done.
-- =============================================================================
