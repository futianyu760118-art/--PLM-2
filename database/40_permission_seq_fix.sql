-- =====================================================================
-- sys_permission 主键序列对齐
-- 背景：02_seed_data.sql 以显式 id(100..1075) 插入权限项，未推进
--       sys_permission_id_seq；R1 新增权限维护接口后首次出现应用侧 INSERT，
--       序列仍从 1 开始，递增到 100 时会与种子行主键冲突。
-- 幂等：可重复执行
-- =====================================================================

SELECT setval(
    pg_get_serial_sequence('sys_permission', 'id'),
    COALESCE((SELECT MAX(id) FROM sys_permission), 0) + 1,
    false
);
