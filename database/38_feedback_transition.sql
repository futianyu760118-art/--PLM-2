-- =============================================================================
-- HJ-PLM 增量 34: 反馈状态流转历史 —— AC F16a「完整状态变更记录」
-- 依据: PAND-149 D2(后一步流转覆盖前一步, 多步历史丢失) + PAND-157 返工项 R-2
-- 内容: 反馈状态流转历史表 + 索引 + 存量数据回填
-- 说明: plm_feedback 上的 evaluate_note/evaluated_by/evaluated_at 仍保留为「最新一次处理」
--       的冗余快照(列表展示用), 但历史轨迹一律以本表为准, 不再被后续流转覆盖。
-- =============================================================================

-- ---------- 1. 状态流转历史表(每次流转追加一行, 只增不改) ----------
CREATE TABLE IF NOT EXISTS plm_feedback_transition (
    id               BIGSERIAL PRIMARY KEY,
    feedback_id      BIGINT      NOT NULL REFERENCES plm_feedback(id), -- 所属反馈
    from_status      VARCHAR(20),                                      -- 变更前状态; 首次提交为 NULL
    to_status        VARCHAR(20) NOT NULL,                             -- 变更后状态(字典 feedback_status)
    note             TEXT,                                             -- 本次分析结论 / 退回原因; 提交记录为固定文案
    related_issue_no VARCHAR(64),                                      -- 立项时回填的 issue 编号, 其余为空
    operator_id      BIGINT,                                           -- 操作人 sys_user.id
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_feedback_transition IS '反馈状态流转历史(AC F16a: 完整状态变更记录)';

-- 时间线按反馈聚合且按时间正序读取
CREATE INDEX IF NOT EXISTS idx_feedback_transition_fb ON plm_feedback_transition (feedback_id, created_at);

-- ---------- 2. 存量数据回填: 已处理过的反馈按当前快照补一条终点记录 ----------
-- 回填只补「当前状态」这一个终点, 中间步骤在旧模型下已不可恢复;
-- 目的仅是不让历史反馈的时间线为空。NOT EXISTS 保证重复执行不产生重复行。
INSERT INTO plm_feedback_transition (feedback_id, from_status, to_status, note, related_issue_no, operator_id, created_at)
SELECT f.id, NULL, f.status, f.evaluate_note, f.related_issue_no, f.evaluated_by,
       COALESCE(f.evaluated_at, f.created_at)
FROM plm_feedback f
WHERE f.evaluated_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM plm_feedback_transition t WHERE t.feedback_id = f.id);
