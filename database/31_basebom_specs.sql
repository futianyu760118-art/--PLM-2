-- =============================================================================
-- HJ-PLM 增量 31: 基础BOM(5基础BOM) + 规格书/配置表字段补齐
-- ① BOM 节点改名「基础BOM」, 对应模板 5基础BOM
-- ② 规格书/配置表按 EBMS「询价管理-详情-规格书/配置表」补齐字段
-- =============================================================================

-- 5基础BOM
CREATE TABLE IF NOT EXISTS plm_base_bom (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(64),
    seq INT, material_code VARCHAR(64), position_no VARCHAR(64), ref_no VARCHAR(64),
    material_name VARCHAR(256), spec VARCHAR(256), aux_attr VARCHAR(64), material_attr VARCHAR(64),
    quantity NUMERIC(18,4), unit VARCHAR(32), sub_type VARCHAR(32), supplier VARCHAR(128),
    key_part VARCHAR(8), use_status VARCHAR(16), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_base_bom_proj ON plm_base_bom(project_no);

-- 规格书补齐 (EBMS: description/power_efficiency/ta/life_time/discharge_time/charging_time/switch_type/reflector_material/inbox_size/carton_size/gw_nw/cable_spec)
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS description VARCHAR(512);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS power_efficiency VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS ta VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS life_time VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS discharge_time VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS charging_time VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS switch_type VARCHAR(64);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS reflector_material VARCHAR(128);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS inbox_size VARCHAR(128);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS carton_size VARCHAR(128);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS gw_nw VARCHAR(128);
ALTER TABLE plm_spec_sheet ADD COLUMN IF NOT EXISTS cable_spec VARCHAR(64);

-- 配置表补齐
ALTER TABLE plm_config_sheet ADD COLUMN IF NOT EXISTS compensated_flux VARCHAR(64);
ALTER TABLE plm_config_sheet ADD COLUMN IF NOT EXISTS discharge_time VARCHAR(64);
ALTER TABLE plm_config_sheet ADD COLUMN IF NOT EXISTS charging_time VARCHAR(64);

-- 节点改名 BOM -> 基础BOM
UPDATE plm_project_node SET node_name='基础BOM', updated_at=NOW() WHERE node_code='BOM';
UPDATE plm_node_sheet_map
   SET node_name='基础BOM',
       worksheet_keys='5基础BOM',
       source_tables='plm_base_bom',
       done_rule='基础BOM齐套/已发布',
       updated_at=NOW()
 WHERE node_code='BOM';

-- =============================================================================
-- Done.
-- =============================================================================
