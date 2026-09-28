-- =============================================================================
-- HJ-PLM 增量 32: 工作表遵循 AEOS 标准 —— 公共信封 + 对象引用
-- 依据 AEOS: contracts/DEH_CONTRACT_V1.md (Common Envelope / Object / Event / Evidence)
--   对象引用: object_ref{object_type,object_id} + external_key(project_no)
--   信封: tenant_id, source_system, revision, created_by, correlation_id
-- =============================================================================

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'plm_plan_sheet','plm_base_bom','plm_spec_sheet','plm_config_sheet','plm_sample',
    'plm_review_sheet','plm_test_report','plm_trial_report','plm_shipment'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32)', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS project_id BIGINT', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS source_system VARCHAR(16)', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS revision INT DEFAULT 0', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by VARCHAR(64)', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64)', t);
  END LOOP;
END $$;

-- 回填 source_system / tenant_id
UPDATE plm_plan_sheet     SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_base_bom       SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_spec_sheet     SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_config_sheet   SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_sample         SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_review_sheet   SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_test_report    SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_trial_report   SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;
UPDATE plm_shipment       SET source_system='PLM2', tenant_id='hjgd' WHERE source_system IS NULL;

-- =============================================================================
-- Done.
-- =============================================================================
