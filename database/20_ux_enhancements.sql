-- =============================================================================
-- HJ-PLM V1.1 增量 20: 用户体验补全
-- ① 多阶梯供应商: plm_material_supplier(料号-供应商-等级-价格-比例)
-- ② 图纸号: plm_material 加 drawing_no 字段
-- ③ BOM 模板(大类骨架): plm_bom_template + plm_bom_template_item
-- ④ 通用实体变更历史: plm_entity_history(全表变更审计,支持对比与回滚)
-- ⑤ BOM 子件排序接口已就绪,sort_order 已存在,本迁移补外键约束
-- =============================================================================

-- ===== ① 多阶梯供应商 =====
CREATE TABLE IF NOT EXISTS plm_material_supplier (
    id              BIGSERIAL PRIMARY KEY,
    material_id     BIGINT NOT NULL REFERENCES plm_material(id) ON DELETE CASCADE,
    supplier_code   VARCHAR(64) NOT NULL,                  -- 供应商编码
    supplier_name   VARCHAR(200) NOT NULL,                 -- 供应商全称
    tier_rank       SMALLINT NOT NULL DEFAULT 1,           -- 阶梯(1首选/2备选/3试产)
    price           DECIMAL(12,4) DEFAULT 0,              -- 含税单价
    currency        VARCHAR(8) DEFAULT 'CNY',
    share_pct       NUMERIC(5,2) DEFAULT 0,                -- 份额%
    lead_time_days  INT DEFAULT 0,                         -- 交期
    moq             INT DEFAULT 0,                          -- 最小起订量
    remark          VARCHAR(500),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_mat_supplier_mat ON plm_material_supplier(material_id, tier_rank);
COMMENT ON TABLE plm_material_supplier IS '物料-供应商阶梯(主供/备选/试产)';

-- ===== ② Material 加 drawing_no + drawing_revision 字段 =====
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS drawing_no VARCHAR(64);
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS drawing_revision VARCHAR(32);
COMMENT ON COLUMN plm_material.drawing_no IS '图纸编号';
COMMENT ON COLUMN plm_material.drawing_revision IS '图纸版本号';

-- ===== ③ BOM 大类模板(结构件/紧固件/包装/电子) =====
CREATE TABLE IF NOT EXISTS plm_bom_template (
    id              BIGSERIAL PRIMARY KEY,
    template_code   VARCHAR(64) NOT NULL UNIQUE,             -- 模板编码
    template_name   VARCHAR(128) NOT NULL,                    -- 模板名(结构件/紧固件/包装/电子…)
    category        VARCHAR(32) NOT NULL,                    -- 大类(structure/fastener/packaging/electronics/standard/custom)
    description     VARCHAR(500),
    product_type    VARCHAR(64),                             -- 适配灯具品类 FL/FLD/BL…
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bom_tpl_cat ON plm_bom_template(category, enabled);

CREATE TABLE IF NOT EXISTS plm_bom_template_item (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES plm_bom_template(id) ON DELETE CASCADE,
    parent_part_no  VARCHAR(64),                             -- 上级料号占位(创建 BOM 时替换为实际 root)
    child_part_no   VARCHAR(64) NOT NULL,                    -- 模板子件料号
    child_name      VARCHAR(200),                            -- 子件名(冗余展示)
    quantity        NUMERIC(14,4) NOT NULL DEFAULT 1,
    unit            VARCHAR(32),
    is_optional     BOOLEAN NOT NULL DEFAULT FALSE,           -- 可选项(可去掉)
    sort_order      INT DEFAULT 0,
    remark          VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_bom_tpl_item_tpl ON plm_bom_template_item(template_id, sort_order);

-- 默认4大类模板:结构件、紧固件、包装、电子(每类带若干常用子件)
INSERT INTO plm_bom_template(template_code,template_name,category,description,sort_order) VALUES
('STRUCTURE_DEFAULT', '灯具-结构件基础骨架', 'structure', '投光灯/泛光灯通用结构件：外壳、散热器、支架、密封圈', 1),
('FASTENER_DEFAULT',  '灯具-紧固件基础骨架', 'fastener',  '通用紧固件：M3/M4/M5 螺丝、螺母、垫片', 2),
('PACKAGING_DEFAULT', '灯具-包装基础骨架', 'packaging', '通用包装：彩盒、外箱、说明书、PE袋、缓冲泡棉', 3),
('ELECTRONICS_DEFAULT','灯具-电子电器基础骨架', 'electronics', '驱动电源、LED光源板、连接线缆', 4)
ON CONFLICT (template_code) DO NOTHING;

-- 结构件模板:散热器/外壳/支架/密封圈/反光罩(常见5项)
INSERT INTO plm_bom_template_item(template_id, child_part_no, child_name, quantity, unit, is_optional, sort_order)
SELECT t.id, 'STRUCT-HEATSINK', '铝合金散热器', 1, 'PCS', false, 1 FROM plm_bom_template t WHERE t.template_code='STRUCTURE_DEFAULT'
UNION ALL SELECT t.id, 'STRUCT-HOUSING', '压铸铝外壳', 1, 'PCS', false, 2 FROM plm_bom_template t WHERE t.template_code='STRUCTURE_DEFAULT'
UNION ALL SELECT t.id, 'STRUCT-BRACKET', '安装支架', 1, 'PCS', false, 3 FROM plm_bom_template t WHERE t.template_code='STRUCTURE_DEFAULT'
UNION ALL SELECT t.id, 'STRUCT-GASKET', '硅胶密封圈', 1, 'PCS', false, 4 FROM plm_bom_template t WHERE t.template_code='STRUCTURE_DEFAULT'
UNION ALL SELECT t.id, 'STRUCT-REFLECTOR', '反光罩', 1, 'PCS', true, 5 FROM plm_bom_template t WHERE t.template_code='STRUCTURE_DEFAULT';

-- 紧固件模板:M3-M5 螺丝/螺母/垫片(常见6项)
INSERT INTO plm_bom_template_item(template_id, child_part_no, child_name, quantity, unit, is_optional, sort_order)
SELECT t.id, 'FAST-M3X8', 'M3×8 内六角螺丝', 4, 'PCS', false, 1 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT'
UNION ALL SELECT t.id, 'FAST-M3NUT', 'M3 螺母', 4, 'PCS', false, 2 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT'
UNION ALL SELECT t.id, 'FAST-M4X10', 'M4×10 内六角螺丝', 2, 'PCS', false, 3 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT'
UNION ALL SELECT t.id, 'FAST-M4NUT', 'M4 螺母', 2, 'PCS', false, 4 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT'
UNION ALL SELECT t.id, 'FAST-M5X12', 'M5×12 内六角螺丝', 2, 'PCS', true, 5 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT'
UNION ALL SELECT t.id, 'FAST-WASHER5', 'M5 弹簧垫圈', 4, 'PCS', false, 6 FROM plm_bom_template t WHERE t.template_code='FASTENER_DEFAULT';

-- 包装模板:彩盒/外箱/说明书/PE袋/泡棉(5项)
INSERT INTO plm_bom_template_item(template_id, child_part_no, child_name, quantity, unit, is_optional, sort_order)
SELECT t.id, 'PKG-COLORBOX', '彩盒(印字)', 1, 'PCS', false, 1 FROM plm_bom_template t WHERE t.template_code='PACKAGING_DEFAULT'
UNION ALL SELECT t.id, 'PKG-OUTERBOX', '五层瓦楞外箱', 1, 'PCS', false, 2 FROM plm_bom_template t WHERE t.template_code='PACKAGING_DEFAULT'
UNION ALL SELECT t.id, 'PKG-MANUAL', '中文说明书', 1, 'PCS', false, 3 FROM plm_bom_template t WHERE t.template_code='PACKAGING_DEFAULT'
UNION ALL SELECT t.id, 'PKG-PE-BAG', 'PE 自封袋', 1, 'PCS', false, 4 FROM plm_bom_template t WHERE t.template_code='PACKAGING_DEFAULT'
UNION ALL SELECT t.id, 'PKG-FOAM', '缓冲泡棉', 2, 'PCS', true, 5 FROM plm_bom_template t WHERE t.template_code='PACKAGING_DEFAULT';

-- 电子电器:驱动/LED 板/连接线/快插端子(4项)
INSERT INTO plm_bom_template_item(template_id, child_part_no, child_name, quantity, unit, is_optional, sort_order)
SELECT t.id, 'ELEC-DRIVER', 'LED 恒流驱动电源', 1, 'PCS', false, 1 FROM plm_bom_template t WHERE t.template_code='ELECTRONICS_DEFAULT'
UNION ALL SELECT t.id, 'ELEC-LED-BOARD', 'LED 光源板', 1, 'PCS', false, 2 FROM plm_bom_template t WHERE t.template_code='ELECTRONICS_DEFAULT'
UNION ALL SELECT t.id, 'ELEC-WIRE', '电源输入线', 1, 'PCS', false, 3 FROM plm_bom_template t WHERE t.template_code='ELECTRONICS_DEFAULT'
UNION ALL SELECT t.id, 'ELEC-CONNECTOR', '快插端子', 2, 'PCS', true, 4 FROM plm_bom_template t WHERE t.template_code='ELECTRONICS_DEFAULT';

-- ===== ④ 通用实体变更历史(差异比对 + 回滚) =====
CREATE TABLE IF NOT EXISTS plm_entity_history (
    id              BIGSERIAL PRIMARY KEY,
    object_type     VARCHAR(32) NOT NULL,                    -- PART / BOM / FILE / ECN ...
    object_id       VARCHAR(64) NOT NULL,                    -- 料号 / BOM id 等
    version         INT NOT NULL DEFAULT 1,                  -- 单调递增版本号
    changed_fields  JSONB NOT NULL,                          -- [{field,before,after,changedBy,changedAt}]
    snapshot_after  JSONB,                                   -- 变更后完整快照(支持回滚)
    change_type     VARCHAR(16) NOT NULL DEFAULT 'UPDATE',   -- CREATE / UPDATE / DELETE / ROLLBACK
    change_source   VARCHAR(32) DEFAULT 'USER',             -- USER / ECN / DQ / JOB
    change_ref      VARCHAR(64),                              -- 关联号(ECN_NO / DQ_RULE_CODE / JOB_NAME)
    changed_by      VARCHAR(64),
    remark          VARCHAR(500),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ent_hist_obj ON plm_entity_history(object_type, object_id, version DESC);
COMMENT ON TABLE plm_entity_history IS '通用实体变更历史(每字段差异 + 快照,支持回滚)';