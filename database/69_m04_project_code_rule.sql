-- =============================================================================
-- 61_m04_project_code_rule.sql
-- M04 项目号编码规则定稿（依业务方 2026-10-31 确认口径）
--
-- 规则定义（业务方原文）：
--   HJ.AAXXX.Y1.ZZZ
--     HJ    = 恒剑（LITERAL）
--     AA    = 年份两位，22 = 2022（YEAR yy）
--     XXX   = 项目序列号 001~999，按立项时间先后流水，年内重置（SEQ 3 位 / YEARLY）
--     Y1    = 产品分类 + 版本号
--               V = 全新产品，V1 = 第一版
--               R = 功能或性能拓展，R1 = 第一版
--               M = 客户定制需求变化，M1 = 第一版
--     ZZZ   = 客户编码，自研 = 000；按数字标准延伸，允许 4 位（如 1008）
--   示例：HJ.22001.R1.631 = 22 年第 1 个项目，功能扩展版，631 客户定向开发
--
-- 补充口径（业务方确认）：
--   客户代码按数字为标准延伸区分 → 客户码位数 3~4 位，纯数字
--   同一客户项目需拆分子项目时，在末尾追加 -N 数字后缀（如 HJ.26031.M1.585-1 / -2）
--
-- 存量校验（线上 192.168.0.71 实测 163 个项目，2026-10-31，严格口径）：
--   严格合规 140 条 85.9% / 字母后缀 14 条 8.6% / 客户码未补零 5 条 3.1%
--   / 分类非法 1 条 / 混入备注 1 条 / 非编号 2 条，明细见视图 v_m04_project_no_bad。
--   注：14 条字母后缀全部为 256JY / 256YL，属客户 256 的两个延伸标识，非项目后缀。
--
-- 幂等：可重复执行。不改变任何既有编号。
-- =============================================================================

-- ---------- 1. 项目号规则定稿（覆盖 60 脚本的占位 pattern）----------
UPDATE plm_code_rule
SET pattern          = 'HJ.{yy}{projSeq:3}.{cls}{clsVer:1}.{custCode:3}{dupSeq}',
    name             = '项目编码 HJ.AAXXX.Y1.ZZZ',
    seq_key          = 'PROJECT_SEQ',
    reset_policy     = 'YEARLY',
    uniqueness_scope = 'GLOBAL',
    allow_manual_override = TRUE,
    override_roles   = 'ADMIN,RD_LEAD',
    updated_at       = NOW()
WHERE rule_code = 'LEGACY_PROJECT';

-- 若 60 尚未执行，此处补建规则行（幂等）
INSERT INTO plm_code_rule (rule_code, object_type, name, version_no, status, pattern, seq_key,
                           reset_policy, uniqueness_scope, allow_manual_override, override_roles)
VALUES ('LEGACY_PROJECT', 'PROJECT', '项目编码 HJ.AAXXX.Y1.ZZZ', 'V1', 'DRAFT',
        'HJ.{yy}{projSeq:3}.{cls}{clsVer:1}.{custCode:3}{dupSeq}', 'PROJECT_SEQ',
        'YEARLY', 'GLOBAL', TRUE, 'ADMIN,RD_LEAD')
ON CONFLICT (rule_code) DO NOTHING;

-- ---------- 2. 分段定义（7 段，含可选重复序号段）----------
-- segment_type 约定：LITERAL 常量 / YEAR 年份 / SEQ 流水 / DICT 字典 / FIELD 取业务字段 / OPT_SEQ 可选序号
DELETE FROM plm_code_rule_segment
WHERE rule_id = (SELECT id FROM plm_code_rule WHERE rule_code = 'LEGACY_PROJECT');

INSERT INTO plm_code_rule_segment (rule_id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
SELECT r.id, v.seq_no, v.segment_type, v.value_expr, v.pad_length, v.dict_type, v.field_path, v.transform
FROM plm_code_rule r,
(VALUES
    (1, 'LITERAL', 'HJ',              NULL, NULL,             NULL,           NULL),
    (2, 'YEAR',    'yy',               2,   NULL,             NULL,           'TWO_DIGIT'),
    (3, 'SEQ',     'PROJECT_SEQ',      3,   'LEGACY_SEQ_NUM3',NULL,           'YEARLY_RESET'),
    (4, 'DICT',    'V,R,M',            1,   'PROJECT_CLASS',  NULL,           'UPPER'),
    (5, 'SEQ',     'PROJECT_CLASS_VER',1,   'LEGACY_SEQ_NAT1',NULL,           'RESET_PER_PARENT_CLASS'),
    (6, 'FIELD',   'customer_code',    3,   NULL,             'customer_code','ZERO_PAD_MIN3_MAX4'),
    (7, 'OPT_SEQ', 'DUP_SUFFIX',       NULL,'LEGACY_SEQ_NAT1','dup_suffix',   'DASH_PREFIXED')
) AS v(seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform)
WHERE r.rule_code = 'LEGACY_PROJECT';

-- ---------- 3. 字典：产品分类 V/R/M ----------
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT v.dict_type, v.dict_value, v.dict_label, v.sort_order, 1
FROM (VALUES
    ('PROJECT_CLASS', 'V', '全新产品（V1 = 第一版）',  1),
    ('PROJECT_CLASS', 'R', '功能/性能拓展（R1 = 第一版）', 2),
    ('PROJECT_CLASS', 'M', '客户定制需求变化（M1 = 第一版）', 3)
) AS v(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value
);

-- ---------- 4. 字典：存量非标准后缀（仅登记，供迁移映射，不用于新编号）----------
-- 实测 163 个项目，字母后缀共 14 条，全部挂在客户 256 上：
--   256JY 11 条 / 256YL 2 条 / "256JY -2" 1 条（含空格）
-- 判断：JY / YL 是客户 256 的延伸标识，不是项目后缀。
-- 按业务口径「客户代码延伸按数字为标准」，应映射为 2561 / 2562，待确认后回填。
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT v.dict_type, v.dict_value, v.dict_label, v.sort_order, 1
FROM (VALUES
    ('LEGACY_DUP_SUFFIX', 'JY',   '客户256延伸标识A，疑似应改 2561（存量 11 例）', 1),
    ('LEGACY_DUP_SUFFIX', 'YL',   '客户256延伸标识B，疑似应改 2562（存量 2 例）',  2),
    ('LEGACY_DUP_SUFFIX', '-N',   '数字延伸后缀，标准写法（-1/-2/-3）',            3)
) AS v(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value
);

-- ---------- 5. 存量校验：视图 + 不合规清单 ----------
-- 判定口径：^HJ\.\d{2}\d{3}\.[VRM]\d\.(\d{3,4})(-\d+)?$
CREATE OR REPLACE VIEW v_m04_project_no_check AS
SELECT
    p.id,
    p.project_no,
    p.project_name,
    p.customer_code,
    CASE
        WHEN p.project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}(-[0-9]+)?$' THEN 'OK'
        WHEN p.project_no !~ '^HJ\.' THEN 'E_NO_PREFIX'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.' THEN 'E_YEAR_SEQ'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.' THEN 'E_CLASS'
        WHEN p.project_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]+' THEN 'E_CUSTOMER'
        ELSE 'E_OTHER'
    END AS check_result,
    -- 客户码不足 3 位时给出补零建议（仅建议，不自动改）
    CASE
        WHEN p.project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{1,2}(-[0-9]+)?$'
        THEN split_part(p.project_no, '.', 1) || '.' || split_part(p.project_no, '.', 2) || '.'
             || split_part(p.project_no, '.', 3) || '.'
             || lpad(split_part(split_part(p.project_no, '.', 4), '-', 1), 3, '0')
             || CASE WHEN split_part(p.project_no, '.', 4) LIKE '%-%'
                     THEN '-' || split_part(split_part(p.project_no, '.', 4), '-', 2) ELSE '' END
        ELSE NULL
    END AS suggested_no
FROM plm_project p;

COMMENT ON VIEW v_m04_project_no_check IS 'M04 项目号规则校验，check_result=OK 为合规；suggested_no 为补零建议（仅建议，不自动改）';

-- 不合规清单（当前实测 8 条）
-- HJ.26005.R1.7-16 / HJ.23048.V1.46 / HJ.26013.R1.46 / HJ.26015.R1.52 / HJ.26025.R1.46  <- 客户码未补零
-- HJ.24017.Y1.256YL                                                               <- 分类字母 Y 非法
-- 预项目 / 237单                                                                    <- 非编号脏数据

-- ---------- 6. 序列：项目年内流水 ----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator)
VALUES
    ('PROJECT_SEQ',        '',   'yy',   3, ''),   -- 项目序列号，年内重置
    ('PROJECT_CLASS_VER',  '',   '',     1, '')    -- 分类版本号，按 项目+分类 重置
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 7. 校验函数：供后端生成前调用 ----------
CREATE OR REPLACE FUNCTION fn_m04_check_project_no(p_no VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    IF p_no IS NULL OR btrim(p_no) = '' THEN RETURN 'E_EMPTY'; END IF;
    IF p_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}(-[0-9]+)?$' THEN RETURN 'OK'; END IF;
    IF p_no !~ '^HJ\.' THEN RETURN 'E_NO_PREFIX'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.' THEN RETURN 'E_YEAR_SEQ'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.' THEN RETURN 'E_CLASS'; END IF;
    IF p_no !~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]+' THEN RETURN 'E_CUSTOMER'; END IF;
    RETURN 'E_OTHER';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- =============================================================================
-- 生效前提（代码侧，未完成前本规则仅作为配置基线，不参与编号生成）：
--   1. CodeGenServiceImpl 当前只读 sys_sequence 拼 {prefix}{yyyyMMdd}{seq}，
--      不读取 plm_code_rule / plm_code_rule_segment，需改造为规则驱动。
--   2. 状态仍为 DRAFT；代码改造 + 业务验收后置为 ENABLED。
--
-- 待业务确认（不猜，留空）：
--   1. 后缀 JY / YL 的真实含义（存量 11 例），确认后才可迁移映射
--   2. 客户码 1008（4 位）是否为正式客户编码，还是 1 + 008 的组合
--   3. 物料号第 4 段：HJ.26044.R1.1008 与项目号同构，若同为客户码，
--      同项目多物料将无法区分 —— 需明确物料号是否另有零件流水段
-- =============================================================================
