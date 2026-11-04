-- =============================================================================
-- 63_m04_customer_code_branch.sql
-- M04 项目号 —— 客户编码段（ZZZ）分支口径定稿
--
-- 业务方 2026-10-31 确认口径（原文）：
--   「1008 是客户代码，有分支就加 -1；前面的 256 是客户代码，JY 是分支，
--     后面 -1 是分支的分支」
--
-- 即客户编码段为三级可扩展结构：
--   第 1 级  客户代码          数字 3~4 位       256 / 585 / 1008 / 000（自研）
--   第 2 级  分支（标签法）    客户码 + 字母标签  256JY / 256YL
--            分支（序号法）    客户码 + -N        585-1 / 585-2 / 000-1
--   第 3 级  分支的分支        客户码 + 标签 + -N  256JY-1 / 256JY-2
--
--   正则：  ^HJ\.\d{5}\.[VRM]\d\.\d{3,4}([A-Z]{1,2})?(-\d+)?$
--
-- 存量实证（线上 192.168.0.71，163 个项目，2026-10-31）：
--   256JY 11 / 256YL 2 / 256JY-1 2 / 256JY -2 1（含空格） / 585-1 1 / 585-2 1
--   000-1 2 / 000-2 1 / 000-3 1 / 1008 1 / 7-16 1
--   口径升级后合规率 85.9%(140) → 93.9%(153)，剩余 10 条见视图。
--
-- 幂等：可重复执行，不修改任何既有编号。
-- 前置：61 已执行（plm_code_rule / 视图 / 函数存在）。
-- =============================================================================

-- ---------- 1. 规则模式串：客户段支持字母分支标签 ----------
UPDATE plm_code_rule
SET pattern      = 'HJ.{yy}{projSeq:3}.{cls}{clsVer:1}.{custCode:3}{branchTag}{dupSeq}',
    name         = '项目编码 HJ.AAXXX.Y1.ZZZ（客户段支持分支标签）',
    updated_at   = NOW()
WHERE rule_code = 'LEGACY_PROJECT';

-- ---------- 2. 分段定义：客户段拆为 客户码 + 分支标签 + 分支序号 ----------
DELETE FROM plm_code_rule_segment
WHERE rule_id = (SELECT id FROM plm_code_rule WHERE rule_code = 'LEGACY_PROJECT');

INSERT INTO plm_code_rule_segment (rule_id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
SELECT r.id, v.seq_no, v.segment_type, v.value_expr, v.pad_length, v.dict_type, v.field_path, v.transform
FROM plm_code_rule r,
(VALUES
    (1, 'LITERAL', 'HJ',                NULL, NULL,               NULL,                NULL),
    (2, 'YEAR',    'yy',                 2,   NULL,               NULL,                'TWO_DIGIT'),
    (3, 'SEQ',     'PROJECT_SEQ',        3,   'LEGACY_SEQ_NUM3',  NULL,                'YEARLY_RESET'),
    (4, 'DICT',    'V,R,M',              1,   'PROJECT_CLASS',    NULL,                'UPPER'),
    (5, 'SEQ',     'PROJECT_CLASS_VER',  1,   'LEGACY_SEQ_NAT1',  NULL,                'RESET_PER_PARENT_CLASS'),
    (6, 'FIELD',   'customer_code',      3,   NULL,               'customer_code',     'ZERO_PAD_MIN3_MAX4'),
    (7, 'OPT_SEQ', 'branch_tag',        NULL, NULL,               'branch_tag',        'UPPER_ALPHA_MAX2'),
    (8, 'OPT_SEQ', 'branch_seq',        NULL, 'LEGACY_SEQ_NAT1',  'branch_seq',        'DASH_PREFIXED')
) AS v(seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
WHERE r.rule_code = 'LEGACY_PROJECT';

-- ---------- 3. 字典：客户分支标签（确认口径，替代原「疑似应改 2561」）----------
UPDATE sys_dict
SET dict_label = '客户 256 的分支标签（第 2 级分支，存量 11 例）',
    status = 1
WHERE dict_type = 'LEGACY_DUP_SUFFIX' AND dict_value = 'JY';

UPDATE sys_dict
SET dict_label = '客户 256 的分支标签（第 2 级分支，存量 2 例）',
    status = 1
WHERE dict_type = 'LEGACY_DUP_SUFFIX' AND dict_value = 'YL';

-- 独立登记分支关系，供后续客户主数据迁移使用
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT v.dict_type, v.dict_value, v.dict_label, v.sort_order, 1
FROM (VALUES
    ('CUSTOMER_BRANCH', '256>JY', '客户 256 分支 JY',   1),
    ('CUSTOMER_BRANCH', '256>YL', '客户 256 分支 YL',   2),
    ('CUSTOMER_BRANCH', 'JY>-N',  '分支 JY 的子分支序号（-1/-2…）', 3)
) AS v(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value
);

-- ---------- 4. 校验视图：升级正则，新增分支段诊断 ----------
CREATE OR REPLACE VIEW v_m04_project_no_check AS
SELECT
    p.id,
    p.project_no,
    p.project_name,
    p.customer_code,
    CASE
        WHEN p.project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}([A-Z]{1,2})?(-[0-9]+)?$' THEN 'OK'
        WHEN p.project_no !~ '^HJ\.' THEN 'E_NO_PREFIX'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.' THEN 'E_YEAR_SEQ'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.' THEN 'E_CLASS'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]+' THEN 'E_CUSTOMER'
        ELSE 'E_OTHER'
    END AS check_result,
    -- 客户码不足 3 位：补零建议（仅建议，不自动改）
    CASE
        WHEN p.project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{1,2}([A-Z]{1,2})?(-[0-9]+)?$'
        THEN regexp_replace(
                p.project_no,
                '(\.)([0-9]{1,2})([A-Z]{1,2})?',
                '\1' || lpad(substring(p.project_no from '\.([0-9]{1,2})[A-Z]?'), 3, '0'),
                'g')
        ELSE NULL
    END AS suggested_no,
    -- 分支段拆解：客户码 / 分支标签 / 分支序号
    substring(p.project_no from '\.([0-9]{3,4})[A-Z]*-?[0-9]*$')                  AS seg_customer,
    substring(p.project_no from '\.[0-9]{3,4}([A-Z]{1,2})')                       AS seg_branch_tag,
    substring(p.project_no from '(-[0-9]+)$')                                     AS seg_branch_seq
FROM plm_project p;

COMMENT ON VIEW v_m04_project_no_check IS
'M04 项目号校验（客户段三级：客户码+分支标签+-N）。check_result=OK 合规；suggested_no 仅建议';

-- ---------- 5. 校验函数：同步新正则 ----------
CREATE OR REPLACE FUNCTION fn_m04_check_project_no(p_no VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    IF p_no IS NULL OR btrim(p_no) = '' THEN RETURN 'E_EMPTY'; END IF;
    IF p_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}([A-Z]{1,2})?(-[0-9]+)?$' THEN RETURN 'OK'; END IF;
    IF p_no !~ '^HJ\.' THEN RETURN 'E_NO_PREFIX'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.' THEN RETURN 'E_YEAR_SEQ'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.' THEN RETURN 'E_CLASS'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]+' THEN RETURN 'E_CUSTOMER'; END IF;
    RETURN 'E_OTHER';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ---------- 6. 客户编码段规范化函数（供后端与迁移共用）----------
-- 输入 '256jy' / ' 256JY -2' / '1008-1' / '自研' 统一规范化
CREATE OR REPLACE FUNCTION fn_m04_norm_customer_seg(p_raw VARCHAR)
RETURNS VARCHAR AS $$
DECLARE
    v VARCHAR;
BEGIN
    IF p_raw IS NULL OR btrim(p_raw) = '' THEN RETURN '000'; END IF;
    v := upper(replace(replace(replace(btrim(p_raw), '（', '('), '）', ')'), ' ', ''));
    IF v LIKE '%自研%' OR v LIKE '%自主%' THEN RETURN '000'; END IF;
    -- 只取「数字 + 可选字母 + 可选 -数字」
    v := (regexp_match(v, '([0-9]{1,4})([A-Z]{1,2})?(-[0-9]+)?'))[0];
    IF v IS NULL THEN RETURN '000'; END IF;
    v := regexp_replace(v, '^([0-9]{1,2})(?=[A-Z-]|$)',
                        lpad(substring(v from '^[0-9]{1,2}'), 3, '0'));
    RETURN v;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- =============================================================================
-- 执行后自检：
--   SELECT check_result, COUNT(*) FROM v_m04_project_no_check GROUP BY 1 ORDER BY 2 DESC;
--   预期：OK 153 / E_CLASS 1 / E_CUSTOMER 4 / E_OTHER 3 / E_NO_PREFIX 2
--   SELECT fn_m04_norm_customer_seg(' 256JY -2')  -> 256JY-2
--   SELECT fn_m04_norm_customer_seg('1008')       -> 1008
-- =============================================================================

-- 剩余待业务确认（不猜，留空）：
--   1. HJ.26005.R1.7-16 中的 7-16：是客户 716 误写，还是客户 7 的分支 16？
--   2. 分类段 Y（HJ.24017.Y1.256YL）：Y 是否为 V 的笔误，或确有第 4 类产品分类？
--   3. 2 位客户码（46 / 52）是否统一补零为 046 / 052（影响 4 条存量）
--   4. 物料/零件号第 4 段与项目号同构（HJ.26044.R1.1008），同项目多零件如何区分
--      —— 需明确零件是否另起一段流水，当前 40 条物料 materialName = partNo，疑似迁移占位
