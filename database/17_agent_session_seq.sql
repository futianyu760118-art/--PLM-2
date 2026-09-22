-- =============================================================================
-- HJ-PLM SME V1.1 增量 17: AGENT_SESSION 编号序列 + 补充缺失序列
-- 说明: AgentController.createSession 依赖 sys_sequence.seq_key='AGENT_SESSION'
-- =============================================================================

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES ('AGENT_SESSION', 'AS', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;
