-- =====================================================================
-- 权限种子存量修复: 能力线 B(订阅/分类/报告) + 能力线 A(证据台账)
--
-- 背景: 46_subscription.sql 早期版本以显式 id 1110..1120 插入权限项, 这些 id 已被
--       36_ebms_users_roles.sql 占用(36 号未指定 id, 由 sys_permission_id_seq 自增
--       得到): 1110=inquiry:import、1111=inquiry:export、1112=product:view、
--       1115=product:delete、1116=customer:view、1117=customer:create、
--       1118=customer:edit、1119=customer:delete、1120=material:view。
--       冲突被 ON CONFLICT (id) DO NOTHING 静默吞掉, 下列权限码从未落库, 对应接口
--       在 @PreAuthorize 上对全部角色(含 ADMIN)返回 403:
--         category:view / category:manage / report:download /
--         subscription:category / subscription:sub / subscription:report /
--         subscription:view / subscription:manage
--       (report:view 因 36 号脚本另有同名权限 id=1126 而幸存)
--
-- 另有同类冲突(第 4~5 节): 45_ledger.sql 早期版本的 id 109/1091/1092 与
--       03_schema_extend.sql 的 archive(109)/archive:view(1091)/archive:upload(1092)
--       冲突, 导致 ledger / ledger:repository / ledger:notify 未落库。
--
-- 本脚本: 按 perm_code 幂等补插缺失权限码 -> 重新补授角色 -> 对齐主键序列。
-- 幂等: 可重复执行。已应用新版 45/46 号脚本的库执行本脚本时, 补插部分为空操作,
--       仅重复一次角色补授(ON CONFLICT DO NOTHING, 无副作用)。
-- 适用: 全新库(00→47)与已应用旧版 45/46 的存量库均可执行。
-- =====================================================================

-- ---------- 1. 确保一级菜单「订阅与报告」存在 ----------
-- 旧版 46 号脚本落在 id=111; 若因故缺失则补插到 2000(与新版 46 号脚本同段)。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
VALUES (2000, 0, 'subscription', '订阅与报告', 1, '/subscription', NULL, 'Bell', 7)
ON CONFLICT (perm_code) DO NOTHING;

-- ---------- 2. 补插缺失权限码(冲突判据为 perm_code, 非 id) ----------
-- 2.1 三个菜单页
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
SELECT v.id, m.id, v.perm_code, v.perm_name, v.perm_type, v.path, v.component, v.icon, v.sort_order
FROM sys_permission m,
     (VALUES
        (2001, 'subscription:category', '分类管理', 1, '/subscription/category', 'subscription/category',    'PriceTag', 1),
        (2002, 'subscription:sub',      '我的订阅', 1, '/subscription/list',     'subscription/subscription', 'Star',    2),
        (2003, 'subscription:report',   '我的报告', 1, '/subscription/report',   'subscription/report',      'Document', 3)
     ) AS v(id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
WHERE m.perm_code = 'subscription'
ON CONFLICT (perm_code) DO NOTHING;

-- 2.2 六个按钮权限(report:view 通常已存在, 按 perm_code 跳过)
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
SELECT v.id, m.id, v.perm_code, v.perm_name, 2, v.sort_order
FROM sys_permission m,
     (VALUES
        (2005, 'subscription:view',   '查看订阅',     1),
        (2006, 'subscription:manage', '管理订阅',     2),
        (2007, 'category:view',       '查看分类维度', 3),
        (2008, 'category:manage',     '管理部门分类', 4),
        (2009, 'report:view',         '查看报告',     5),
        (2010, 'report:download',     '下载报告',     6)
     ) AS v(id, perm_code, perm_name, sort_order)
WHERE m.perm_code = 'subscription'
ON CONFLICT (perm_code) DO NOTHING;

-- ---------- 3. 重新补授角色 ----------
-- 旧版 46 的授权语句以 perm_code 关联 sys_permission, 权限码缺失时授出空集,
-- 故补插后必须重跑一遍(与 46_subscription.sql 第 11 节同口径)。
-- ADMIN: 全量权限(旧授权是补插前的一次性快照, 不含这 8 个权限码)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 部门分类管理员: 分类管理 + 订阅与报告
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'CATEGORY_ADMIN' AND p.perm_code IN
      ('subscription','subscription:category','subscription:sub','subscription:report',
       'subscription:view','subscription:manage','category:view','category:manage',
       'report:view','report:download')
ON CONFLICT DO NOTHING;

-- 台账管理员 / 台账录入人 / 台账查看人 / 研发工程师 / 品质 / 销售: 订阅与报告 + 分类查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('LEDGER_ADMIN', 'LEDGER_EDITOR', 'LEDGER_VIEWER', 'ENGINEER', 'QUALITY', 'SALES')
  AND p.perm_code IN
      ('subscription','subscription:category','subscription:sub','subscription:report',
       'subscription:view','subscription:manage','category:view','report:view','report:download')
ON CONFLICT DO NOTHING;

-- ---------- 4. 台账权限(能力线 A)同类冲突修复 ----------
-- 45_ledger.sql 早期版本以显式 id 109/1091/1092 插入, 与 03_schema_extend.sql 的
-- archive(109)/archive:view(1091)/archive:upload(1092) 冲突, 被 DO NOTHING 吞掉,
-- 导致 ledger / ledger:repository / ledger:notify 未落库。前端 router 的
-- meta.permission='ledger' 因此无法对任何角色授予, 非 ADMIN 角色被守卫拦在台账页外。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
VALUES (2100, 0, 'ledger', '证据台账', 1, '/ledger', NULL, 'Files', 6)
ON CONFLICT (perm_code) DO NOTHING;

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
SELECT v.id, m.id, v.perm_code, v.perm_name, 1, v.path, v.component, v.icon, v.sort_order
FROM sys_permission m,
     (VALUES
        (2101, 'ledger:ledger',     '台账录入',   1, '/ledger/list',         'ledger/index',        'Document',     1),
        (2102, 'ledger:repository', '资料汇总处', 1, '/ledger/repository',   'ledger/repository',   'FolderOpened', 2),
        (2103, 'ledger:notify',     '变更通知',   1, '/ledger/notification', 'ledger/notification', 'Bell',         3)
     ) AS v(id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
WHERE m.perm_code = 'ledger'
ON CONFLICT (perm_code) DO NOTHING;

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
SELECT v.id, m.id, v.perm_code, v.perm_name, 2, v.sort_order
FROM sys_permission m,
     (VALUES
        (2105, 'ledger:view',     '查看证据', 1),
        (2106, 'ledger:add',      '新建证据', 2),
        (2107, 'ledger:edit',     '编辑证据', 3),
        (2108, 'ledger:delete',   '删除证据', 4),
        (2109, 'ledger:download', '下载资料', 5)
     ) AS v(id, perm_code, perm_name, sort_order)
WHERE m.perm_code = 'ledger'
ON CONFLICT (perm_code) DO NOTHING;

-- ---------- 5. 台账权限归位 ----------
-- 存量库中已落库的 ledger:* 行 parent_id 指向 109(实为 archive 菜单), 改挂到
-- ledger 菜单下, 否则侧边栏会把台账页渲染成档案菜单的子项。
UPDATE sys_permission c
SET parent_id = m.id
FROM sys_permission m
WHERE m.perm_code = 'ledger'
  AND c.perm_code LIKE 'ledger:%'
  AND c.parent_id <> m.id;

-- ---------- 6. 台账角色补授 ----------
-- (旧 45 的授权语句以 perm_code 关联, 权限码缺失时授出空集)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 台账管理员: 全部台账权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'LEDGER_ADMIN' AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify',
       'ledger:view','ledger:add','ledger:edit','ledger:delete','ledger:download')
ON CONFLICT DO NOTHING;

-- 台账录入人 / 研发工程师: 查看/新建/编辑/下载, 无删除
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('LEDGER_EDITOR', 'ENGINEER') AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify',
       'ledger:view','ledger:add','ledger:edit','ledger:download')
ON CONFLICT DO NOTHING;

-- 台账查看人 / 品质 / 销售: 仅查看与下载
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('LEDGER_VIEWER', 'QUALITY', 'SALES') AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify','ledger:view','ledger:download')
ON CONFLICT DO NOTHING;

-- ---------- 7. 主键序列对齐 ----------
-- 第 2/4 节以显式 id 插入 2000+/2100+ 段, 需推进序列, 否则应用侧新增菜单会主键冲突
-- (与 40_permission_seq_fix.sql 同因同法)。
SELECT setval(
    pg_get_serial_sequence('sys_permission', 'id'),
    COALESCE((SELECT MAX(id) FROM sys_permission), 0) + 1,
    false
);
