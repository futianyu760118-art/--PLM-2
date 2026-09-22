-- =====================================================================
-- Step6: 洞察生成 Job 支撑 —— 洞察编号序列
-- DQ 夜检后聚合 TOP 债务规则 → 生成 plm_analytics_insight；HIGH 自动转 plm_issue
-- 幂等：可重复执行。
-- =====================================================================

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES ('INSIGHT_NO', 'INS', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;
