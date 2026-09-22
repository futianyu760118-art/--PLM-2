CREATE TABLE IF NOT EXISTS plm_bom_item (
    id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL REFERENCES plm_bom(id) ON DELETE CASCADE,
    parent_item_id BIGINT DEFAULT 0,
    path LTREE,
    level_no INT DEFAULT 1,
    parent_part_no VARCHAR(64),
    part_no VARCHAR(64) NOT NULL,
    material_id BIGINT NOT NULL REFERENCES plm_material(id),
    part_name VARCHAR(200),
    quantity NUMERIC(14,4) DEFAULT 1,
    material_texture VARCHAR(100),
    specification VARCHAR(255),
    unit VARCHAR(32),
    make_type SMALLINT DEFAULT 0,
    version_no VARCHAR(32),
    model3d_file_id BIGINT,
    drawing_file_id BIGINT,
    sort_order INT DEFAULT 0,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bom_item_bom ON plm_bom_item(bom_id);
CREATE INDEX IF NOT EXISTS idx_bom_item_parent ON plm_bom_item(parent_item_id);
CREATE INDEX IF NOT EXISTS idx_bom_item_part ON plm_bom_item(part_no);
CREATE INDEX IF NOT EXISTS idx_bom_item_path ON plm_bom_item USING GIST (path);
