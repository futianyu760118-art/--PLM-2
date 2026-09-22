-- =============================================================================
-- HJ-PLM V1.1 增量 21: 修复枚举值缺失
-- 物料 "转量产" 时 status='IN_PRODUCTION',但 archive_status_enum 未声明该值。
-- 补充枚举值,确保 MaterialStatus.IN_PRODUCTION 能落库。
-- =============================================================================

ALTER TYPE archive_status_enum ADD VALUE IF NOT EXISTS 'IN_PRODUCTION';

-- =============================================================================
-- ADMIN 全量权限补授 (初始化链末尾执行)
-- 02 号种子授全量时, 后续脚本(03/10/12...)新增的权限尚不存在,
-- 此处重新对 ADMIN 授予当前全部权限, 保证 admin 拥有完整权限树。
-- =============================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;