-- =====================================================================
-- 恒剑光电 PLM 中台 V4.0 初始化数据
-- 说明: 六大固定角色、权限矩阵、初始管理员、字典数据
-- =====================================================================

-- ---------- 1. 部门 ----------
INSERT INTO sys_department (dept_code, dept_name, parent_id, sort_order) VALUES
('RD',       '研发中心',   NULL, 1),
('QA',       '品质部',     NULL, 2),
('PROD',     '生产部',     NULL, 3),
('SALES',    '销售部',     NULL, 4),
('SCM',      '供应链中心', NULL, 5);

-- ---------- 2. 固定六大角色 ----------
INSERT INTO sys_role (role_code, role_name, role_level, data_scope, builtin, remark) VALUES
('ADMIN',     '系统管理员',  9, 1, 1, '全部读写、配置、删除、权限分配、日志查看'),
('ENGINEER',  '研发工程师',  5, 1, 1, '3D/2D/ECN/模具台账/工艺 全部读写; 可发起变更、发图审批'),
('QUALITY',   '品质/生产',   3, 2, 1, '仅查看产品结构、BOM、工艺、检验标准; 屏蔽模具3D、加工图纸'),
('SALES',     '销售',        2, 2, 1, '仅外观3D、产品参数、成品BOM; 屏蔽内部结构、模具数据'),
('SUPPLIER',  '外协供应商',  1, 3, 1, '仅当期生效外协图纸包, 无历史、无原版、无结构预览'),
('CUSTOMER',  '外部客户',    0, 3, 1, '仅外网脱敏3D外观预览, 零图纸、零尺寸、零结构');

-- ---------- 3. 权限/菜单 ----------
-- 菜单(一级)
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order) VALUES
(100, 0, 'material',   '物料管理',   1, '/material',   'material/index',   'Box',     1),
(101, 0, 'ecn',        'ECN变更',    1, '/ecn',        'ecn/index',        'Switch',  2),
(102, 0, 'bom',        'BOM管理',    1, '/bom',        'bom/index',        'Connection', 3),
(103, 0, 'mold',       '模具资产',   1, '/mold',       'mold/index',       'Grid',    4),
(104, 0, 'quality',    '品质标准',   1, '/quality',    'quality/index',    'CircleCheck', 5),
(107, 0, 'system',     '系统管理',   1, '/system',     NULL,               'Setting', 99);

-- 系统管理子菜单
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order) VALUES
(1071, 107, 'system:user',       '用户管理', 1, '/system/user',       'system/user',       'User',     1),
(1072, 107, 'system:role',       '角色管理', 1, '/system/role',       'system/role',       'UserFilled', 2),
(1073, 107, 'system:permission', '权限管理', 1, '/system/permission', 'system/permission', 'Key',      3),
(1074, 107, 'system:log',        '操作日志', 1, '/system/log',        'system/log',        'Document', 4),
(1075, 107, 'system:dict',       '字典管理', 1, '/system/dict',       'system/dict',       'Tickets',  5);

-- 按钮权限(物料模块示例)
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1001, 100, 'material:add',     '新增物料', 2, 1),
(1002, 100, 'material:edit',    '编辑物料', 2, 2),
(1003, 100, 'material:delete',  '删除物料', 2, 3),
(1004, 100, 'material:import',  '导入物料', 2, 4),
(1005, 100, 'material:export',  '导出物料', 2, 5),
(1006, 100, 'material:release', '发布物料', 2, 6),
(1007, 100, 'material:change',  '发起变更', 2, 7),

(1011, 101, 'ecn:add',      '新建ECN',  2, 1),
(1012, 101, 'ecn:review1',  '一审审批', 2, 2),
(1013, 101, 'ecn:review2',  '二审审批', 2, 3),
(1014, 101, 'ecn:effect',   '生效执行', 2, 4),
(1015, 101, 'ecn:export',   '导出ECN',  2, 5),

(1021, 102, 'bom:add',      '新建BOM',  2, 1),
(1022, 102, 'bom:edit',     '编辑BOM',  2, 2),
(1023, 102, 'bom:explode',  '3D爆炸生成', 2, 3),
(1024, 102, 'bom:compare',  '版本对比', 2, 4),
(1025, 102, 'bom:export',   '导出BOM',  2, 5);

-- ---------- 4. 角色权限分配 ----------
-- 系统管理员: 全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN';

-- 研发工程师: 物料/ECN/BOM 全部 + 系统:日志查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ENGINEER'
  AND p.perm_code IN (
    'material','ecn','bom','mold','quality','outsourcing','archive',
    'material:add','material:edit','material:import','material:export','material:release','material:change',
    'ecn:add','ecn:review1','ecn:export',
    'bom:add','bom:edit','bom:explode','bom:compare','bom:export',
    'system:log'
  );

-- 品质/生产: 仅查看 BOM + 品质标准 + 档案(无下载)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'QUALITY'
  AND p.perm_code IN ('bom','quality','archive');

-- 销售: 仅外观/产品参数/成品BOM
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SALES'
  AND p.perm_code IN ('material','bom:export');

-- 外协/客户: 无菜单权限(通过专用链接访问)

-- ---------- 5. 初始管理员账号 ----------
-- 密码: admin@123 (BCrypt 加密 - Java BCryptPasswordEncoder 生成)
INSERT INTO sys_user (username, password, real_name, employee_no, email, dept_id, status, remark) VALUES
('admin', '$2a$10$RpjbkW80EN1n/hykBU5oUeLkM1UR.hm.DECgQWPiPTE0J3Fd.Xwjy', '系统管理员', 'ADMIN001', 'admin@hjgd.com', 1, 1, '超级管理员(初始化)');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

-- ---------- 6. 字典数据 ----------
-- 物料类型
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('material_type', '成品',   'FINISHED', 1),
('material_type', '半成品', 'SEMI',     2),
('material_type', '塑胶件', 'PLASTIC',  3),
('material_type', '五金件', 'HARDWARE', 4),
('material_type', '标准件', 'STANDARD', 5);

-- 物料状态
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('material_status', '草稿',       'DRAFT',         1),
('material_status', '评审中',     'REVIEWING',     2),
('material_status', '正式发布',   'RELEASED',      3),
('material_status', '量产在用',   'IN_PRODUCTION', 4),
('material_status', '变更中',     'CHANGING',      5),
('material_status', '作废',       'OBSOLETE',      6),
('material_status', '停产封存',   'SEALED',        7);

-- ECN 变更类型
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('ecn_change_type', '结构变更', 'STRUCTURE', 1),
('ecn_change_type', '模具变更', 'MOLD',      2),
('ecn_change_type', '工艺变更', 'PROCESS',   3),
('ecn_change_type', 'BOM变更',  'BOM',       4),
('ecn_change_type', '尺寸变更', 'DIMENSION', 5);

-- ECN 审批状态
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('ecn_status', '草稿',     'DRAFT',       1),
('ecn_status', '待一审',   'PENDING_L1',  2),
('ecn_status', '待二审',   'PENDING_L2',  3),
('ecn_status', '审批通过', 'APPROVED',    4),
('ecn_status', '审批驳回', 'REJECTED',    5),
('ecn_status', '已生效',   'EFFECTIVE',   6),
('ecn_status', '已作废',   'VOID',        7);

-- ---------- 7. 编号序列 ----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length) VALUES
('PART_NO',  'HJ',    'yyyyMMdd', 4),   -- 料号: HJ+yyyyMMdd+0001
('ECN_NO',   'ECN',   'yyyyMMdd', 4),   -- ECN: ECN+yyyyMMdd+0001
('BOM_NO',   'BOM',   'yyyyMMdd', 4);   -- BOM: BOM+yyyyMMdd+0001

-- ---------- 8. 启用 ltree 扩展(用于BOM物化路径) ----------
CREATE EXTENSION IF NOT EXISTS ltree;
