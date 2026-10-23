-- =============================================================================
-- HJ-PLM 增量 56: 反馈流程升级为 7 步闭环
-- 状态机(业务 status):
--   pending(待分析) → proposed(待确认方案) → approved(待实现) → implementing(实现中)
--   → implemented(待闭环) → resolved(待管理员确认) → verified(待合并) → merged(已合并)
--   closed(已关闭/驳回) 为旁路终态，可重开回 pending。
-- ai_status(none/queued/running/done/failed) 仅表示 AI 执行子状态，不再驱动业务状态。
-- =============================================================================

-- ---------- 1. 方案与各节点审计字段 ----------
ALTER TABLE IF EXISTS plm_feedback
    ADD COLUMN IF NOT EXISTS proposal      TEXT,
    ADD COLUMN IF NOT EXISTS proposal_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reject_note   TEXT,
    ADD COLUMN IF NOT EXISTS approved_by   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS approved_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_by     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS closed_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verified_by   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS verified_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS merged_by     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merged_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS merged_branch VARCHAR(128);

-- ---------- 2. 存量数据归位: 旧 processing(处理中) → approved(待实现) ----------
-- 旧模型下 processing 表示「已有人接手/AI 已答复」，与新模型最接近的是 approved(方案已确认待实现)。
-- 已 resolved/closed 的保持原值，历史数据不回填中间节点。
UPDATE plm_feedback SET status = 'approved' WHERE status = 'processing';

-- ---------- 3. 字典: 覆盖 feedback_status 为新流水线 ----------
DELETE FROM sys_dict WHERE dict_type = 'feedback_status';

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('feedback_status', '待分析',       'pending',      1),
('feedback_status', '待确认方案',   'proposed',     2),
('feedback_status', '待实现',       'approved',     3),
('feedback_status', '实现中',       'implementing', 4),
('feedback_status', '待闭环',       'implemented',  5),
('feedback_status', '待管理员确认', 'resolved',     6),
('feedback_status', '待合并',       'verified',     7),
('feedback_status', '已合并',       'merged',       8),
('feedback_status', '已关闭',       'closed',       9);
