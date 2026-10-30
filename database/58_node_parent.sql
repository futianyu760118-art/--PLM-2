-- 58: 节点支持追加子项(如模具重开记录)：加 parent_code 列
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS parent_code VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_plm_project_node_parent ON plm_project_node(project_id, parent_code);
