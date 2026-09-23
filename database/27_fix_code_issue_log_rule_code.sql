-- =====================================================================
-- Phase 0 止血返工 (D3): plm_code_issue_log 列口径统一
--
-- 背景: 10_v1_1_lean_agent_kpi.sql 建表用 rule_id/rule_version,
--       11_wave0_part_lifecycle.sql 建表用 rule_code。
--       10 先执行, 表已存在, 11 的 CREATE TABLE IF NOT EXISTS 成了空操作,
--       最终表里没有 rule_code 列; 而 CodeGenServiceImpl.allocate 正是往
--       rule_code 写值 → PostgreSQL 报「列不存在」。该语句虽被 try/catch 吞掉,
--       但错误已把当前事务置为 aborted(25P02), 同事务内后续语句全部失败,
--       表现即干净库上 POST /material 建料号失败。
--
-- 处理: 对已建库补齐缺失列; 10/11 两个建表脚本的口径已在此次一并统一,
--       全新库不再依赖本脚本。
-- =====================================================================

ALTER TABLE plm_code_issue_log ADD COLUMN IF NOT EXISTS rule_code    VARCHAR(64);
ALTER TABLE plm_code_issue_log ADD COLUMN IF NOT EXISTS rule_id      BIGINT;
ALTER TABLE plm_code_issue_log ADD COLUMN IF NOT EXISTS rule_version VARCHAR(16);
