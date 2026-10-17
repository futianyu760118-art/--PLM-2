-- =============================================================================
-- HJ-PLM 增量 48: 用户数据可见范围(data_scope)
-- 依据: 需求「登录用户名与显示内容强相关，可配置看到/操作范围」
-- 语义: 1=全部(默认，所有人都能看到全部内容), 2=仅他人(排除自己), 3=仅本人; ADMIN 始终全部
-- 幂等: ADD COLUMN IF NOT EXISTS
-- =============================================================================
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS data_scope SMALLINT DEFAULT 1;
COMMENT ON COLUMN sys_user.data_scope IS '数据可见范围: 1=全部(默认), 2=仅他人, 3=仅本人';
