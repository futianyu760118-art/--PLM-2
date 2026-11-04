-- =============================================================================
-- 65_m04_document_center.sql
-- M04 文档中心（T05–T07）配套 DDL / 数据
--   1. 兜底补列（64 号已补则跳过，保证本脚本可独立执行）
--   2. DOC 文档编号序列 + 编码规则 + 分段（走 64 号的规则引擎）
--   3. 权限点：document:*
--   4. 字典：doc_type / doc_lifecycle_state / doc_secret_level
--   5. 索引补强
--   6. 自检
-- 幂等：可重复执行。执行顺序：64 → 65 → 发布后端 → 发布前端
-- =============================================================================

BEGIN;

-- ---------- 1. 兜底补列（若 64 已执行，此处为空操作）----------
ALTER TABLE sys_sequence      ADD COLUMN IF NOT EXISTS last_period VARCHAR(16);
ALTER TABLE plm_code_rule     ADD COLUMN IF NOT EXISTS sample_code VARCHAR(128);
ALTER TABLE plm_code_rule     ADD COLUMN IF NOT EXISTS priority    INT NOT NULL DEFAULT 100;

-- ---------- 2. 文档编号：DOC + yyyyMMdd + 4 位流水 ----------
INSERT INTO sys_sequence (seq_key, prefix, current_val, date_pattern, length)
VALUES ('DOC_NO', 'DOC', 0, 'yyyyMMdd', 4)
ON CONFLICT (seq_key) DO NOTHING;

INSERT INTO plm_code_rule (rule_code, object_type, name, version_no, status, pattern,
                           seq_key, reset_policy, uniqueness_scope, preview_enabled,
                           allow_manual_override, sample_code, priority)
VALUES ('DOC_NO_V1', 'DOC', '文档编号', 'V1', 'ACTIVE', 'DOC+yyyyMMdd+0000',
        'DOC_NO', 'DAILY', 'GLOBAL', TRUE, TRUE, 'DOC202610310001', 100)
ON CONFLICT (rule_code) DO NOTHING;

-- 分段：PREFIX 取 sys_sequence.prefix，运行时可改前缀而无需动规则
INSERT INTO plm_code_rule_segment (rule_id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
SELECT r.id, v.seq_no, v.segment_type, v.value_expr, v.pad_length, NULL, NULL, NULL
FROM plm_code_rule r
JOIN (VALUES
    (1, 'PREFIX', NULL::varchar, NULL::int),
    (2, 'DATE',   'yyyyMMdd',   NULL),
    (3, 'SEQ',    NULL,         4)
) AS v(seq_no, segment_type, value_expr, pad_length) ON TRUE
WHERE r.rule_code = 'DOC_NO_V1'
  AND NOT EXISTS (
      SELECT 1 FROM plm_code_rule_segment s WHERE s.rule_id = r.id AND s.seq_no = v.seq_no
  );

-- ---------- 3. 权限点 ----------
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, sort_order, visible, status)
SELECT 0, v.code, v.name, 2, v.sort, 1, 1
FROM (VALUES
    ('document:add',           '新增文档',     1),
    ('document:edit',          '编辑文档',     2),
    ('document:delete',        '删除文档',     3),
    ('document:release',       '文档五区流转', 4),
    ('document:checkout',      '检入检出',     5),
    ('document:acl',           '文档权限配置', 6),
    ('document:override-code', '手工指定文档编号', 7)
) AS v(code, name, sort)
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_code = v.code);

-- admin 角色授予全部文档权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'admin'
  AND p.perm_code LIKE 'document:%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ---------- 4. 字典 ----------
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.t, v.l, v.v, v.s
FROM (VALUES
    ('doc_type', '图纸',     'DRAWING', 1),
    ('doc_type', '标准',     'STANDARD',2),
    ('doc_type', '工艺文件', 'PROCESS', 3),
    ('doc_type', '规格书',   'SPEC',    4),
    ('doc_type', '说明书',   'MANUAL',  5),
    ('doc_type', '体系文件', 'SYSTEM',  6),
    ('doc_type', '其他',     'OTHER',   7),

    ('doc_lifecycle_state', '工作区', 'WORKING',  1),
    ('doc_lifecycle_state', '发布区', 'RELEASED', 2),
    ('doc_lifecycle_state', '归档区', 'ARCHIVED', 3),
    ('doc_lifecycle_state', '报废区', 'OBSOLETE', 4),
    ('doc_lifecycle_state', '回收区', 'RECYCLE',  5),

    ('doc_secret_level', '公开',   'PUBLIC',   1),
    ('doc_secret_level', '内部',   'INTERNAL', 2),
    ('doc_secret_level', '保密',   'SECRET',   3)
) AS v(t, l, v, s)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.t AND d.dict_value = v.v
);

-- ---------- 5. 索引补强 ----------
CREATE INDEX IF NOT EXISTS idx_doc_owner      ON plm_document(owner);
CREATE INDEX IF NOT EXISTS idx_doc_checkout   ON plm_document(checked_out_by) WHERE checked_out_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_docver_checksum ON plm_document_version(checksum);
CREATE INDEX IF NOT EXISTS idx_docacl_subject ON plm_document_acl(subject_type, subject_id);

-- =============================================================================
-- 6. 自检（在提交前执行：不通过则整脚本回滚）
-- =============================================================================
DO $$
DECLARE
    v_rule   INT;
    v_seg    INT;
    v_perm   INT;
    v_dict   INT;
    v_seq    INT;
BEGIN
    SELECT count(*) INTO v_seq  FROM sys_sequence  WHERE seq_key = 'DOC_NO';
    SELECT count(*) INTO v_rule FROM plm_code_rule WHERE rule_code = 'DOC_NO_V1' AND status = 'ACTIVE';
    SELECT count(*) INTO v_seg  FROM plm_code_rule_segment s
        JOIN plm_code_rule r ON r.id = s.rule_id WHERE r.rule_code = 'DOC_NO_V1';
    SELECT count(*) INTO v_perm FROM sys_permission WHERE perm_code LIKE 'document:%';
    SELECT count(*) INTO v_dict FROM sys_dict WHERE dict_type IN
        ('doc_type','doc_lifecycle_state','doc_secret_level');

    RAISE NOTICE 'DOC 序列=% (期望 1) / 规则=% (期望 1) / 分段=% (期望 3) / 权限点=% (期望 7) / 字典=% (期望 15)',
        v_seq, v_rule, v_seg, v_perm, v_dict;

    IF v_seq <> 1 OR v_rule <> 1 OR v_seg <> 3 THEN
        RAISE EXCEPTION '65 号脚本自检未通过：序列/规则/分段不完整';
    END IF;
END $$;

COMMIT;

-- =============================================================================
-- 回滚（如需）
-- =============================================================================
-- DELETE FROM plm_code_rule WHERE rule_code = 'DOC_NO_V1';   -- 分段级联删除
-- DELETE FROM sys_sequence WHERE seq_key = 'DOC_NO';
-- DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE perm_code LIKE 'document:%');
-- DELETE FROM sys_permission WHERE perm_code LIKE 'document:%';
-- DELETE FROM sys_dict WHERE dict_type IN ('doc_type','doc_lifecycle_state','doc_secret_level');
-- DROP INDEX IF EXISTS idx_doc_owner, idx_doc_checkout, idx_docver_checksum, idx_docacl_subject;
-- 注意：文档表本身由 59 号脚本创建，回滚本脚本不删表，避免误删业务数据。
