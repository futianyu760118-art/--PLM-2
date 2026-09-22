-- =====================================================================
-- Step4-A/B: 档案树模板配置化 + 灯具参数模板
-- 替换 ArchiveTreeTemplate.java 硬编码：按 part_category/product_type/material_type
-- 解析档案树模板；灯具成品套用灯具专用参数模板。
-- 幂等：可重复执行。
-- =====================================================================

-- ===== A. 档案树模板 =====
CREATE TABLE IF NOT EXISTS plm_archive_tree_tpl (
    id                  BIGSERIAL PRIMARY KEY,
    tpl_code            VARCHAR(64) UNIQUE NOT NULL,
    tpl_name            VARCHAR(128) NOT NULL,
    match_category      VARCHAR(64),                 -- PRODUCT/ASSEMBLY/COMPONENT/STANDARD/PACKAGING，NULL=不限
    match_product_type  VARCHAR(64),                 -- FL/WL/HB/STR...，NULL=不限
    match_material_type VARCHAR(64),                 -- PLASTIC/HARDWARE/... 逗号分隔，NULL=不限
    priority            INT NOT NULL DEFAULT 0,      -- 解析优先级，大者优先
    enabled             SMALLINT NOT NULL DEFAULT 1,
    remark              VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_archive_tree_tpl IS '档案目录树模板(按品类匹配)';

CREATE TABLE IF NOT EXISTS plm_archive_tree_tpl_node (
    id          BIGSERIAL PRIMARY KEY,
    tpl_code    VARCHAR(64) NOT NULL,
    node_code   VARCHAR(64) NOT NULL,
    node_name   VARCHAR(128) NOT NULL,
    parent_code VARCHAR(64),                         -- NULL=顶级
    level_no    INT NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    is_leaf     SMALLINT NOT NULL DEFAULT 1,
    UNIQUE (tpl_code, node_code)
);
COMMENT ON TABLE plm_archive_tree_tpl_node IS '档案目录树模板节点';

-- ===== B. 参数模板 =====
CREATE TABLE IF NOT EXISTS plm_param_tpl (
    id                  BIGSERIAL PRIMARY KEY,
    tpl_code            VARCHAR(64) UNIQUE NOT NULL,
    tpl_name            VARCHAR(128) NOT NULL,
    match_category      VARCHAR(64),
    match_product_type  VARCHAR(64),
    match_material_type VARCHAR(64),
    priority            INT NOT NULL DEFAULT 0,
    enabled             SMALLINT NOT NULL DEFAULT 1,
    remark              VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE plm_param_tpl IS '参数模板(按品类匹配)';

-- 兼容历史:若表已存在但缺 remark 列则补
ALTER TABLE plm_param_tpl ADD COLUMN IF NOT EXISTS remark VARCHAR(255);

CREATE TABLE IF NOT EXISTS plm_param_tpl_item (
    id          BIGSERIAL PRIMARY KEY,
    tpl_code    VARCHAR(64) NOT NULL,
    param_key   VARCHAR(64) NOT NULL,
    param_name  VARCHAR(128) NOT NULL,
    unit        VARCHAR(32),
    data_type   VARCHAR(16) NOT NULL DEFAULT 'TEXT', -- TEXT/NUMBER/ENUM/BOOL
    dict_type   VARCHAR(64),                         -- ENUM 时关联 sys_dict
    required    SMALLINT NOT NULL DEFAULT 0,
    dq_severity VARCHAR(8) NOT NULL DEFAULT 'INFO',  -- 缺失时 DQ 级别 BLOCK/WARN/INFO
    sort_order  INT NOT NULL DEFAULT 0,
    UNIQUE (tpl_code, param_key)
);
COMMENT ON TABLE plm_param_tpl_item IS '参数模板项';

-- 物料参数实际值(key-value，不再往 plm_material 堆列)
CREATE TABLE IF NOT EXISTS plm_material_param (
    id          BIGSERIAL PRIMARY KEY,
    part_no     VARCHAR(64) NOT NULL,
    param_key   VARCHAR(64) NOT NULL,
    param_value VARCHAR(512),
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (part_no, param_key)
);
CREATE INDEX IF NOT EXISTS idx_material_param_part ON plm_material_param(part_no);
COMMENT ON TABLE plm_material_param IS '物料参数值(按参数模板承载)';


-- =====================================================================
-- A. 档案树模板种子
-- =====================================================================

-- ---------- 灯具成品 ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, match_product_type, priority, remark) VALUES
('LUMINAIRE_FG', '灯具成品档案树', 'PRODUCT', NULL, 100, '投光/工矿/路灯等成品灯具通用')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('LUMINAIRE_FG','01','01_外观评审档',            NULL,  1,  1, 1),
('LUMINAIRE_FG','02','02_产品结构3D档案',         NULL,  1,  2, 1),
('LUMINAIRE_FG','03','03_光学设计与测试',         NULL,  1,  3, 0),
('LUMINAIRE_FG','0301','01_IES分布光度文件',      '03',  2,  1, 1),
('LUMINAIRE_FG','0302','02_光通量与光效测试',     '03',  2,  2, 1),
('LUMINAIRE_FG','0303','03_光束角与配光曲线',     '03',  2,  3, 1),
('LUMINAIRE_FG','0304','04_色温显指SDCM测试',     '03',  2,  4, 1),
('LUMINAIRE_FG','04','04_安规认证',               NULL,  1,  4, 0),
('LUMINAIRE_FG','0401','01_CCC_CE证书',           '04',  2,  1, 1),
('LUMINAIRE_FG','0402','02_IP防护等级测试',       '04',  2,  2, 1),
('LUMINAIRE_FG','0403','03_EMC电磁兼容报告',      '04',  2,  3, 1),
('LUMINAIRE_FG','0404','04_RoHS环保报告',         '04',  2,  4, 1),
('LUMINAIRE_FG','05','05_电源驱动方案',           NULL,  1,  5, 0),
('LUMINAIRE_FG','0501','01_驱动规格书',           '05',  2,  1, 1),
('LUMINAIRE_FG','0502','02_电气原理图',           '05',  2,  2, 1),
('LUMINAIRE_FG','0503','03_安规电源3D',           '05',  2,  3, 1),
('LUMINAIRE_FG','06','06_散热设计(铝基板_温升)',  NULL,  1,  6, 1),
('LUMINAIRE_FG','07','07_2D产品加工图纸',         NULL,  1,  7, 1),
('LUMINAIRE_FG','08','08_BOM清单(全版本)',        NULL,  1,  8, 1),
('LUMINAIRE_FG','09','09_ECN变更记录(全历史)',    NULL,  1,  9, 1),
('LUMINAIRE_FG','10','10_检验标准(IQC_IPQC_FQC)', NULL,  1, 10, 1),
('LUMINAIRE_FG','11','11_包装设计',               NULL,  1, 11, 0),
('LUMINAIRE_FG','1101','01_卡通箱彩盒',           '11',  2,  1, 1),
('LUMINAIRE_FG','1102','02_标签丝印',             '11',  2,  2, 1),
('LUMINAIRE_FG','1103','03_说明书',               '11',  2,  3, 1),
('LUMINAIRE_FG','12','12_客户确认档',             NULL,  1, 12, 1),
('LUMINAIRE_FG','13','13_试产与老化报告',         NULL,  1, 13, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;

-- ---------- 模具件(塑胶/五金，沿用原硬编码28节点) ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, match_material_type, priority, remark) VALUES
('MOLD_PART', '模具件档案树(塑胶_五金)', 'COMPONENT', 'PLASTIC,HARDWARE', 90, '含模具3D/加工图/试模履历')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('MOLD_PART','01','01_外观评审档',            NULL,  1,  1, 1),
('MOLD_PART','02','02_产品结构3D档案',         NULL,  1,  2, 1),
('MOLD_PART','03','03_模流分析报告',           NULL,  1,  3, 1),
('MOLD_PART','04','04_模具3D拆模总档',         NULL,  1,  4, 0),
('MOLD_PART','0401','01_整套模具总装配STEP',   '04',  2,  1, 1),
('MOLD_PART','0402','02_前模仁独立3D',         '04',  2,  2, 1),
('MOLD_PART','0403','03_后模仁独立3D',         '04',  2,  3, 1),
('MOLD_PART','0404','04_行位斜顶镶件拆分3D',   '04',  2,  4, 1),
('MOLD_PART','0405','05_流道排气槽水路3D',     '04',  2,  5, 1),
('MOLD_PART','0406','06_模胚整套3D装配',       '04',  2,  6, 1),
('MOLD_PART','05','05_2D模具加工图纸包',       NULL,  1,  5, 0),
('MOLD_PART','0501','01_模具总装开模图',       '05',  2,  1, 1),
('MOLD_PART','0502','02_CNC模仁加工图',        '05',  2,  2, 1),
('MOLD_PART','0503','03_EDM铜公放电线电极清单','05',  2,  3, 1),
('MOLD_PART','0504','04_线割加工图',           '05',  2,  4, 1),
('MOLD_PART','0505','05_模具水路运水图',       '05',  2,  5, 1),
('MOLD_PART','0506','06_排气槽薄骨位标注图',   '05',  2,  6, 1),
('MOLD_PART','0507','07_模胚加工图',           '05',  2,  7, 1),
('MOLD_PART','0508','08_单件零件散件加工图',   '05',  2,  8, 1),
('MOLD_PART','06','06_试模修模履历',           NULL,  1,  6, 1),
('MOLD_PART','07','07_注塑成型工艺标准',       NULL,  1,  7, 1),
('MOLD_PART','08','08_2D产品加工图纸',         NULL,  1,  8, 1),
('MOLD_PART','09','09_BOM清单(全版本)',        NULL,  1,  9, 1),
('MOLD_PART','10','10_ECN变更记录(全历史)',    NULL,  1, 10, 1),
('MOLD_PART','11','11_检验标准(IQC_IPQC)',     NULL,  1, 11, 1),
('MOLD_PART','12','12_模具资产台账',           NULL,  1, 12, 1),
('MOLD_PART','13','13_外协发图记录',           NULL,  1, 13, 1),
('MOLD_PART','14','14_客户确认档',             NULL,  1, 14, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;

-- ---------- 通用部件(非模具：电路板/线材/五金外购等) ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, priority, remark) VALUES
('GENERIC_COMPONENT', '通用部件档案树', 'COMPONENT', 10, '无模具的通用部件')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('GENERIC_COMPONENT','01','01_2D加工图纸',      NULL, 1, 1, 1),
('GENERIC_COMPONENT','02','02_3D模型',          NULL, 1, 2, 1),
('GENERIC_COMPONENT','03','03_规格承认书',      NULL, 1, 3, 1),
('GENERIC_COMPONENT','04','04_BOM清单',         NULL, 1, 4, 1),
('GENERIC_COMPONENT','05','05_ECN变更记录',     NULL, 1, 5, 1),
('GENERIC_COMPONENT','06','06_检验标准(IQC)',   NULL, 1, 6, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;

-- ---------- 半成品/组件 ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, priority, remark) VALUES
('ASSEMBLY', '半成品组件档案树', 'ASSEMBLY', 100, '半成品/组件')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('ASSEMBLY','01','01_产品结构3D档案',     NULL, 1, 1, 1),
('ASSEMBLY','02','02_2D加工图纸',         NULL, 1, 2, 1),
('ASSEMBLY','03','03_装配SOP',            NULL, 1, 3, 1),
('ASSEMBLY','04','04_BOM清单(全版本)',    NULL, 1, 4, 1),
('ASSEMBLY','05','05_ECN变更记录(全历史)',NULL, 1, 5, 1),
('ASSEMBLY','06','06_检验标准(IPQC)',     NULL, 1, 6, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;

-- ---------- 标准件 ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, priority, remark) VALUES
('STANDARD', '标准件档案树', 'STANDARD', 100, '标准件/外购通用件')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('STANDARD','01','01_规格书承认书',   NULL, 1, 1, 1),
('STANDARD','02','02_供应商资料',     NULL, 1, 2, 1),
('STANDARD','03','03_RoHS环保报告',   NULL, 1, 3, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;

-- ---------- 兜底默认(无任何匹配时) ----------
INSERT INTO plm_archive_tree_tpl (tpl_code, tpl_name, match_category, match_product_type, match_material_type, priority, remark) VALUES
('GENERIC_DEFAULT', '默认档案树(兜底)', NULL, NULL, NULL, 1, '无匹配模板时使用')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_archive_tree_tpl_node (tpl_code, node_code, node_name, parent_code, level_no, sort_order, is_leaf) VALUES
('GENERIC_DEFAULT','01','01_技术资料',         NULL, 1, 1, 1),
('GENERIC_DEFAULT','02','02_BOM清单',          NULL, 1, 2, 1),
('GENERIC_DEFAULT','03','03_ECN变更记录',      NULL, 1, 3, 1),
('GENERIC_DEFAULT','04','04_检验标准',         NULL, 1, 4, 1)
ON CONFLICT (tpl_code, node_code) DO NOTHING;


-- =====================================================================
-- B. 参数模板种子
-- =====================================================================

-- ---------- 灯具参数模板 ----------
INSERT INTO plm_param_tpl (tpl_code, tpl_name, match_category, priority, remark) VALUES
('LUMINAIRE_PARAM', '灯具参数模板', 'PRODUCT', 100, '成品灯具光学/电气/安规参数')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_param_tpl_item (tpl_code, param_key, param_name, unit, data_type, dict_type, required, dq_severity, sort_order) VALUES
('LUMINAIRE_PARAM','ip_rating','IP防护等级',  NULL,  'ENUM',  'ip_rating',   1, 'WARN',  1),
('LUMINAIRE_PARAM','power_w','额定功率',      'W',   'NUMBER',NULL,          1, 'WARN',  2),
('LUMINAIRE_PARAM','luminous_flux_lm','光通量','lm', 'NUMBER',NULL,          0, 'INFO',  3),
('LUMINAIRE_PARAM','efficacy_lm_w','光效',    'lm/W','NUMBER',NULL,          0, 'INFO',  4),
('LUMINAIRE_PARAM','cct_k','色温',            'K',   'ENUM',  'cct',         0, 'INFO',  5),
('LUMINAIRE_PARAM','beam_angle','光束角',     '°',   'ENUM',  'beam_angle',  0, 'INFO',  6),
('LUMINAIRE_PARAM','cri_ra','显色指数Ra',     NULL,  'NUMBER',NULL,          0, 'INFO',  7),
('LUMINAIRE_PARAM','sdcm','色容差SDCM',       NULL,  'NUMBER',NULL,          0, 'INFO',  8),
('LUMINAIRE_PARAM','input_voltage_v','输入电压','V', 'TEXT',  NULL,          0, 'INFO',  9),
('LUMINAIRE_PARAM','dimming_type','调光方式', NULL,  'ENUM',  'dimming_type',0, 'INFO', 10),
('LUMINAIRE_PARAM','light_source','光源类型', NULL,  'ENUM',  'light_source',0, 'INFO', 11),
('LUMINAIRE_PARAM','power_factor','功率因数PF',NULL, 'NUMBER',NULL,          0, 'INFO', 12),
('LUMINAIRE_PARAM','protection_class','防触电保护等级',NULL,'TEXT',NULL,     0, 'INFO', 13),
('LUMINAIRE_PARAM','rated_life_h','额定寿命', 'h',   'NUMBER',NULL,          0, 'INFO', 14),
('LUMINAIRE_PARAM','emitting_size','发光面尺寸',NULL,'TEXT',  NULL,          0, 'INFO', 15)
ON CONFLICT (tpl_code, param_key) DO NOTHING;

-- ---------- 通用部件参数模板 ----------
INSERT INTO plm_param_tpl (tpl_code, tpl_name, match_category, priority, remark) VALUES
('COMPONENT_PARAM', '通用部件参数模板', 'COMPONENT', 10, '电气/规格基础参数')
ON CONFLICT (tpl_code) DO NOTHING;

INSERT INTO plm_param_tpl_item (tpl_code, param_key, param_name, unit, data_type, required, dq_severity, sort_order) VALUES
('COMPONENT_PARAM','rated_voltage','额定电压','V','TEXT',0,'INFO',1),
('COMPONENT_PARAM','rated_current','额定电流','A','TEXT',0,'INFO',2),
('COMPONENT_PARAM','material_spec','规格',NULL,'TEXT',0,'INFO',3)
ON CONFLICT (tpl_code, param_key) DO NOTHING;


-- =====================================================================
-- 灯具字典(sys_dict)
-- sys_dict 无 (dict_type,dict_value) 唯一约束，用 NOT EXISTS 保证幂等
-- =====================================================================

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('product_type','投光灯','FL',1),
('product_type','工作灯','WL',2),
('product_type','工矿灯','HB',3),
('product_type','路灯','STR',4),
('product_type','隧道灯','TL',5),
('product_type','庭院灯','GD',6),
('product_type','平板灯','PL',7),
('product_type','筒灯','DL',8),
('product_type','玉米灯','CSL',9)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('ip_rating','IP20','IP20',1),
('ip_rating','IP44','IP44',2),
('ip_rating','IP54','IP54',3),
('ip_rating','IP65','IP65',4),
('ip_rating','IP66','IP66',5),
('ip_rating','IP67','IP67',6),
('ip_rating','IP68','IP68',7)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('cct','2700K(暖白)','2700K',1),
('cct','3000K(暖白)','3000K',2),
('cct','3500K(自然白)','3500K',3),
('cct','4000K(自然白)','4000K',4),
('cct','5000K(正白)','5000K',5),
('cct','5700K(正白)','5700K',6),
('cct','6500K(冷白)','6500K',7)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('beam_angle','15°','15',1),
('beam_angle','25°','25',2),
('beam_angle','30°','30',3),
('beam_angle','45°','45',4),
('beam_angle','60°','60',5),
('beam_angle','90°','90',6),
('beam_angle','120°','120',7)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('dimming_type','不调光','NONE',1),
('dimming_type','TRIAC可控硅','TRIAC',2),
('dimming_type','0-10V','0-10V',3),
('dimming_type','PWM','PWM',4),
('dimming_type','DALI','DALI',5),
('dimming_type','智能调光','SMART',6)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('light_source','SMD贴片','SMD',1),
('light_source','COB集成','COB',2),
('light_source','模组','MODULE',3),
('light_source','灯丝','FILAMENT',4)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type=v.dict_type AND d.dict_value=v.dict_value);
