-- 75: 节点完成审批（上传证据后由工程师提交，经理审批通过才置为已完成）
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_approval_status VARCHAR(30);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_submitted_by VARCHAR(64);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_submitted_at TIMESTAMPTZ;
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_approved_by VARCHAR(64);
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_approved_at TIMESTAMPTZ;
ALTER TABLE plm_project_node ADD COLUMN IF NOT EXISTS completion_reject_note TEXT;
