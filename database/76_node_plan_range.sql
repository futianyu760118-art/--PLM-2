-- 76: 节点计划改为「计划开始/计划结束」时间段（保留 plan_date 兼容）
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_start_date DATE;
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS plan_end_date DATE;
UPDATE plm_project_node SET plan_start_date = plan_date WHERE plan_start_date IS NULL AND plan_date IS NOT NULL;
UPDATE plm_project_node SET plan_end_date = plan_date WHERE plan_end_date IS NULL AND plan_date IS NOT NULL;
