-- =============================================================================
-- HJ-PLM 增量 35: 立项申请书自由文本列放宽为 TEXT (联动版含长文本, 原 64/128 溢出)
-- =============================================================================
ALTER TABLE plm_project_initiation ALTER COLUMN market_status TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN target_market TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN cooperators  TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN team_requirement TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN project_name TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN customer_type TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN customer_level TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN has_competitor TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN dev_type TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN purchase_cycle TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN customer_no TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN project_type TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN department TYPE TEXT;
ALTER TABLE plm_project_initiation ALTER COLUMN owner TYPE TEXT;

-- =============================================================================
-- Done.
-- =============================================================================
