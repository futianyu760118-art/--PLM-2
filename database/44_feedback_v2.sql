-- =============================================================================
-- HJ-PLM 增量 44: 反馈模块 v2（对齐中台 SPC 反馈模块，替换旧 plm_feedback）
-- 依据: 复制 gitee/ningbohengjianguangdian/spc 的反馈模块（含 AI 自动实现）
-- 说明: 旧 plm_feedback/plm_feedback_transition 保留为 _legacy，不删除，便于回滚。
-- 幂等: ALTER TABLE IF EXISTS ... RENAME 与 CREATE TABLE IF NOT EXISTS。
-- =============================================================================

-- ---------- 1. 旧表降级为 legacy ----------
ALTER TABLE IF EXISTS plm_feedback RENAME TO plm_feedback_legacy;
ALTER TABLE IF EXISTS plm_feedback_transition RENAME TO plm_feedback_transition_legacy;

-- 旧表索引名与新表重名，先释放（legacy 仅作回滚留底，索引可重建）
DROP INDEX IF EXISTS idx_feedback_dedup;
DROP INDEX IF EXISTS idx_feedback_status;
DROP INDEX IF EXISTS idx_feedback_created;
DROP INDEX IF EXISTS idx_feedback_submitter;

-- ---------- 2. 新反馈表（字段对齐 SPC feedback 模型） ----------
CREATE TABLE IF NOT EXISTS plm_feedback (
    id               BIGSERIAL PRIMARY KEY,
    type             VARCHAR(16)  NOT NULL DEFAULT 'issue',      -- issue/suggestion/inquiry
    severity         VARCHAR(16)  NOT NULL DEFAULT 'low',        -- low/medium/high
    page_path        VARCHAR(256),                               -- 所在页面
    title            VARCHAR(256) NOT NULL,
    description      TEXT         NOT NULL,
    attachments      TEXT,                                       -- StoredFile JSON 数组 [{key,url,name,size,mime}]
    status           VARCHAR(16)  NOT NULL DEFAULT 'pending',    -- pending/processing/resolved/closed

    reply            TEXT,                                       -- 管理员回复
    replied_by       VARCHAR(64),
    replied_at       TIMESTAMPTZ,

    submitter_id     BIGINT       NOT NULL,
    submitter_name   VARCHAR(64)  NOT NULL,

    -- AI 自动实现
    ai_status        VARCHAR(16)  NOT NULL DEFAULT 'none',       -- none/queued/running/done/failed
    ai_branch        VARCHAR(128),                               -- runner 产出的分支名
    ai_note          TEXT,                                       -- runner 结果摘要/失败原因
    ai_requested_by  VARCHAR(64),
    ai_requested_at  TIMESTAMPTZ,
    ai_stage         VARCHAR(32),                                -- preparing/fetching/agent/gate_backend/gate_frontend/committing
    ai_stage_started_at TIMESTAMPTZ,
    ai_progress      TEXT,                                       -- 执行记录 [{at,kind,stage,text}]
    ai_result        TEXT,                                       -- {branch,summary,files,gates,runDir,error}

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_feedback IS '问题反馈(对齐中台 SPC，含 AI 自动实现)';

CREATE INDEX IF NOT EXISTS idx_feedback_type      ON plm_feedback (type);
CREATE INDEX IF NOT EXISTS idx_feedback_severity  ON plm_feedback (severity);
CREATE INDEX IF NOT EXISTS idx_feedback_status    ON plm_feedback (status);
CREATE INDEX IF NOT EXISTS idx_feedback_ai_status ON plm_feedback (ai_status);
CREATE INDEX IF NOT EXISTS idx_feedback_submitter ON plm_feedback (submitter_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created   ON plm_feedback (created_at);

-- ---------- 3. 字典（类型/严重度/状态） ----------
DELETE FROM sys_dict WHERE dict_type IN ('feedback_type', 'feedback_severity', 'feedback_status');

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('feedback_type',     '问题', 'issue',      1),
('feedback_type',     '建议', 'suggestion', 2),
('feedback_type',     '咨询', 'inquiry',    3),
('feedback_severity', '低',   'low',        1),
('feedback_severity', '中',   'medium',     2),
('feedback_severity', '高',   'high',       3),
('feedback_status',   '待处理', 'pending',    1),
('feedback_status',   '处理中', 'processing', 2),
('feedback_status',   '已解决', 'resolved',   3),
('feedback_status',   '已关闭', 'closed',     4);

-- ---------- 4. 权限：复用 feedback:manage（37 已种子），补齐 ADMIN 授权 ----------
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN' AND p.perm_code = 'feedback:manage'
ON CONFLICT DO NOTHING;
