-- =============================================================================
-- HJ-PLM 增量 25: 进度节点证据 - 系统内填写与预览
-- 证据来源: FILE(上传文件) / TEXT(系统内填写)
-- =============================================================================

ALTER TABLE plm_project_node_evidence ADD COLUMN IF NOT EXISTS content    TEXT;
ALTER TABLE plm_project_node_evidence ADD COLUMN IF NOT EXISTS source     VARCHAR(16) NOT NULL DEFAULT 'FILE';
ALTER TABLE plm_project_node_evidence ADD COLUMN IF NOT EXISTS mime_type  VARCHAR(128);
ALTER TABLE plm_project_node_evidence ADD COLUMN IF NOT EXISTS version_no VARCHAR(16) DEFAULT 'V1';
COMMENT ON COLUMN plm_project_node_evidence.source IS '证据来源: FILE文件/TEXT系统内填写';
COMMENT ON COLUMN plm_project_node_evidence.content IS '系统内填写的证据内容(富文本/纯文本)';
