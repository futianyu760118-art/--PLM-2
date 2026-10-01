-- =============================================================================
-- HJ-PLM 增量 34: 修复逻辑删除列 deleted 默认值与空值
-- 现象: 部分表 deleted 无默认值, 插入为 NULL, 而 MyBatis-Plus 逻辑删除按 deleted=0 过滤,
--       导致新增记录"不可见"(查询/动作 404)。统一设默认 0 并回填空值。
-- =============================================================================

DO $$
DECLARE t text;
BEGIN
  FOR t IN
    SELECT table_name FROM information_schema.columns
    WHERE table_schema='public' AND column_name='deleted'
  LOOP
    EXECUTE format('ALTER TABLE %I ALTER COLUMN deleted SET DEFAULT 0', t);
    EXECUTE format('UPDATE %I SET deleted=0 WHERE deleted IS NULL', t);
  END LOOP;
END $$;

-- =============================================================================
-- Done.
-- =============================================================================
