-- =============================================================================
-- HJ-PLM 增量 45: 证据台账（能力线 A）
-- 依据: PAND-198 需求 AC(A1-A6) + PAND-199 架构方案 §3.1-3.3/§3.7/§10
-- 内容: 台账主表 + 附件引用 + 关联对象 + 站内通知 + 通知开关
--       + 编号序列 + 字典种子 + 菜单权限与台账角色种子
-- 幂等: CREATE TABLE IF NOT EXISTS / ON CONFLICT DO NOTHING
--
-- 设计要点（与架构一致）:
--   1) 「证据台账」与「资料汇总处」是同一实体 plm_ledger 的两个视图, 不建第二事实源。
--   2) 附件只存 plm_file 引用, 不复制文件本体。
--   3) 「双向同步」= 单一事实源 + 零副本 + 引用视图; plm_ledger_link 只记录引用关系。
--   4) 站内通知随能力 A 落地(AC-A4-7~A4-11 属能力 A), 能力 B 的报告推送复用本表。
-- =============================================================================

-- ---------- 1. 台账主表: 证据台账 = 资料汇总处的唯一事实源 ----------
CREATE TABLE IF NOT EXISTS plm_ledger (
    id                BIGSERIAL PRIMARY KEY,
    ledger_no         VARCHAR(32)  NOT NULL UNIQUE,             -- 证据编号 EV-yyyyMMdd-NNNN(服务端生成, 全局唯一)
    name              VARCHAR(100),                             -- 证据名称 <=100(AC-A2-3); 草稿可空(AC-A2-7)
    type              VARCHAR(32),                              -- 证据类型(字典 ledger_type, AC-A2-6); 草稿可空
    owner_id          BIGINT,                                   -- 责任人 sys_user.id(AC-A2-2); 草稿可空
    occur_date        DATE,                                     -- 发生时间, 不得晚于当前日期(AC-A2-4); 草稿可空
    version           VARCHAR(20),                              -- 版本 <=20(AC-A2-3)
    dept_id           BIGINT,                                   -- 来源部门 sys_department.id(AC-A2); 草稿可空
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',    -- DRAFT/SUBMITTED/ARCHIVED/VOID(AC-A2-7)
    remark            VARCHAR(500),                             -- 备注 <=500(AC-A2-3)
    uploaded_by       BIGINT,                                   -- 上传人 sys_user.id
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),      -- 上传时间(系统生成, AC-A2-1)
    last_modified_by  VARCHAR(64),                              -- 最后修改人(AC-A3-4)
    last_modified_at  TIMESTAMPTZ,                              -- 最后修改时间(AC-A3-4/并发 LWW 依据 AC-A3-7)
    revision          INT          NOT NULL DEFAULT 0,          -- 乐观锁版本(AC-A3-7)
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted           SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_ledger IS '证据台账(=资料汇总处唯一事实源), 能力线 A';
COMMENT ON COLUMN plm_ledger.ledger_no IS '证据编号 EV-yyyyMMdd-NNNN, sys_sequence(LEDGER_NO) 生成';
COMMENT ON COLUMN plm_ledger.status IS '草稿 DRAFT(不校验必填) / 已提交 SUBMITTED / 已归档 ARCHIVED / 作废 VOID';
-- name/type/owner_id/occur_date/dept_id 保持可空: 草稿允许暂缺必填项(AC-A2-7),
-- 「提交态必有值」由服务层 validateEntityFields 在 SUBMITTED 时强制, 避免半成品草稿写不进来。

CREATE INDEX IF NOT EXISTS idx_ledger_no          ON plm_ledger (ledger_no);
CREATE INDEX IF NOT EXISTS idx_ledger_type        ON plm_ledger (type);
CREATE INDEX IF NOT EXISTS idx_ledger_owner       ON plm_ledger (owner_id);
CREATE INDEX IF NOT EXISTS idx_ledger_dept        ON plm_ledger (dept_id);
CREATE INDEX IF NOT EXISTS idx_ledger_status      ON plm_ledger (status);
CREATE INDEX IF NOT EXISTS idx_ledger_occur_date  ON plm_ledger (occur_date);
CREATE INDEX IF NOT EXISTS idx_ledger_uploaded_at ON plm_ledger (uploaded_at);
CREATE INDEX IF NOT EXISTS idx_ledger_name        ON plm_ledger (name);

-- ---------- 2. 附件: 只存 plm_file 引用, 不复制文件本体 ----------
CREATE TABLE IF NOT EXISTS plm_ledger_attachment (
    id           BIGSERIAL PRIMARY KEY,
    ledger_id    BIGINT      NOT NULL,
    file_id      BIGINT      NOT NULL,                 -- -> plm_file.id(文件本体在 plm_file)
    file_name    VARCHAR(256),                         -- 冗余文件名(列表展示, 免联查)
    file_ext     VARCHAR(32),                          -- 冗余扩展名(白名单/预览判定)
    file_size    BIGINT,                               -- 冗余大小(展示)
    sort_order   INT         NOT NULL DEFAULT 0,
    uploaded_by  BIGINT,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_ledger_attachment IS '台账附件(仅引用 plm_file, 单条上限 20 个 AC-A1-4)';

CREATE INDEX IF NOT EXISTS idx_ledger_attach_ledger ON plm_ledger_attachment (ledger_id);
CREATE INDEX IF NOT EXISTS idx_ledger_attach_file   ON plm_ledger_attachment (file_id);
-- 同一台账同一文件只挂一次(软删行不占位, 支持删除后重新挂载)
CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_attach ON plm_ledger_attachment (ledger_id, file_id) WHERE deleted = 0;

-- ---------- 3. 关联对象: 证据 <-> 业务模块 多态引用(AC-A4) ----------
CREATE TABLE IF NOT EXISTS plm_ledger_link (
    id              BIGSERIAL PRIMARY KEY,
    ledger_id       BIGINT       NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,             -- PROJECT/REQUIREMENT/MATERIAL/BOM/ECN/TASK/REVIEW
    object_id       VARCHAR(64)  NOT NULL,             -- 目标业务键(project_no / issue_no / part_no / bom_no / ecn_no / 主键)
    object_no       VARCHAR(64),                       -- 冗余编号(对象失效后仍可展示)
    object_name     VARCHAR(256),                      -- 冗余名称
    link_status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    invalid_reason  VARCHAR(32),                       -- DELETED/OBSOLETE(AC-A4-5)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_ledger_link IS '证据与业务对象的关联(多态引用); 模块侧证据区按本表联查 plm_ledger, 不存副本';

CREATE INDEX IF NOT EXISTS idx_ledger_link_ledger ON plm_ledger_link (ledger_id);
CREATE INDEX IF NOT EXISTS idx_ledger_link_object ON plm_ledger_link (object_type, object_id);
-- 单条证据对同一对象只关联一次(AC-A4-1 关联数上限默认 20 由服务层计数)
CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_link ON plm_ledger_link (ledger_id, object_type, object_id) WHERE deleted = 0;

-- ---------- 4. 站内通知(AC-A4-7~A4-11; 能力 B 报告推送复用) ----------
CREATE TABLE IF NOT EXISTS plm_notification (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,               -- 接收人
    notify_type   VARCHAR(32)  NOT NULL,               -- LEDGER_CHANGED/LEDGER_CREATED/LEDGER_DELETED/LEDGER_LINK/OBJ_INVALID/SUB_ITEM/SUB_REPORT
    title         VARCHAR(256) NOT NULL,
    content_json  JSONB,                               -- {objectNo,objectName,changes:[{field,before,after}],operator,time,link}
    object_type   VARCHAR(32),
    object_id     VARCHAR(64),
    report_id     BIGINT,                              -- 能力 B 报告推送预留
    read_status   SMALLINT     NOT NULL DEFAULT 0,     -- 0未读 1已读
    agg_key       VARCHAR(128),                        -- 聚合键 user+type+object+5分钟窗口(AC-A4-10)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_notification IS '站内通知(变更聚合 + 已读状态); 送达 <=1 分钟(AC-A4-9)';

CREATE INDEX IF NOT EXISTS idx_notification_user   ON plm_notification (user_id, read_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_agg    ON plm_notification (agg_key);

-- ---------- 5. 通知订阅开关(AC-A4-11) ----------
CREATE TABLE IF NOT EXISTS plm_notification_setting (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    notify_type  VARCHAR(32) NOT NULL,
    enabled      SMALLINT    NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_notification_setting IS '按通知类型的订阅开关(AC-A4-11), 未配置视为开启';

CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_setting ON plm_notification_setting (user_id, notify_type) WHERE deleted = 0;

-- ---------- 6. 编号序列: 复用 sys_sequence ----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator)
VALUES ('LEDGER_NO', 'EV-', 'yyyyMMdd', 4, '-')
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 7. 字典种子: 证据类型 / 台账状态 / 关联对象类型 ----------
-- sys_dict 无唯一键, 幂等只能靠 WHERE NOT EXISTS(与 13_archive_param_templates.sql 同口径)
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('ledger_type', '设计输入', 'DESIGN_INPUT', 1),
('ledger_type', '评审记录', 'REVIEW_RECORD', 2),
('ledger_type', '试验报告', 'TEST_REPORT', 3),
('ledger_type', '检验报告', 'INSPECT_REPORT', 4),
('ledger_type', '变更凭证', 'CHANGE_PROOF', 5),
('ledger_type', '借阅记录', 'BORROW_RECORD', 6)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('ledger_status', '草稿', 'DRAFT', 1),
('ledger_status', '已提交', 'SUBMITTED', 2),
('ledger_status', '已归档', 'ARCHIVED', 3),
('ledger_status', '作废', 'VOID', 4)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('ledger_object_type', '项目', 'PROJECT', 1),
('ledger_object_type', '需求', 'REQUIREMENT', 2),
('ledger_object_type', '零件/物料', 'MATERIAL', 3),
('ledger_object_type', 'BOM', 'BOM', 4),
('ledger_object_type', '变更单', 'ECN', 5),
('ledger_object_type', '任务', 'TASK', 6),
('ledger_object_type', '评审', 'REVIEW', 7)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

-- ---------- 8. 菜单权限种子(复用 sys_permission) ----------
-- 一级菜单「证据台账」+ 三页: 台账录入 / 资料汇总处 / 变更通知; 按钮权限 2105..2109。
-- 注: 使用 2100+ 高位段。原用 id 109/1091/1092 与 03_schema_extend.sql 的
--     archive(109)/archive:view(1091)/archive:upload(1092) 冲突, 被 DO NOTHING
--     静默吞掉, 导致 ledger / ledger:repository / ledger:notify 未落库;
--     冲突判据改为 perm_code。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
VALUES
(2100,   0,   'ledger',          '证据台账',   1, '/ledger',           NULL,                    'Files',    6),
(2101,  2100, 'ledger:ledger',   '台账录入',   1, '/ledger/list',      'ledger/index',          'Document', 1),
(2102,  2100, 'ledger:repository','资料汇总处', 1, '/ledger/repository','ledger/repository',     'FolderOpened', 2),
(2103,  2100, 'ledger:notify',   '变更通知',   1, '/ledger/notification','ledger/notification','Bell',     3)
ON CONFLICT (perm_code) DO NOTHING;

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
VALUES
(2105, 2100, 'ledger:view',     '查看证据',   2, 1),
(2106, 2100, 'ledger:add',      '新建证据',   2, 2),
(2107, 2100, 'ledger:edit',     '编辑证据',   2, 3),
(2108, 2100, 'ledger:delete',   '删除证据',   2, 4),
(2109, 2100, 'ledger:download', '下载资料',   2, 5)
ON CONFLICT (perm_code) DO NOTHING;

-- ---------- 9. 台账角色种子(AC-A6 角色表: 台账管理员 / 录入人 / 查看人) ----------
-- data_scope: 1=全部 2=本部门 3=本人 (sys_role.data_scope, 见 01_schema.sql)
INSERT INTO sys_role (role_code, role_name, role_level, data_scope, builtin, remark)
VALUES
('LEDGER_ADMIN',  '台账管理员', 6, 1, 0, '证据台账: 全部查看/新建/编辑/删除/下载(AC-A6)'),
('LEDGER_EDITOR', '台账录入人', 4, 2, 0, '证据台账: 本部门+被关联对象相关查看; 本人创建/本部门可编辑; 不可删除(AC-A6)'),
('LEDGER_VIEWER', '台账查看人', 2, 2, 0, '证据台账: 有权限范围内查看与下载; 不可新建/编辑/删除(AC-A6)')
ON CONFLICT (role_code) DO NOTHING;

-- ADMIN: 全量权限补授(02 号种子的 ADMIN 授权是初始化快照, 之后新增的权限不在其中)
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

-- 录入人: 查看/新建/编辑/下载, 无删除
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'LEDGER_EDITOR' AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify',
       'ledger:view','ledger:add','ledger:edit','ledger:download')
ON CONFLICT DO NOTHING;

-- 查看人: 仅查看/下载
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'LEDGER_VIEWER' AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify','ledger:view','ledger:download')
ON CONFLICT DO NOTHING;

-- 既有业务角色按职责对齐(研发工程师=录入人, 品质/销售=查看人)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ENGINEER' AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify',
       'ledger:view','ledger:add','ledger:edit','ledger:download')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('QUALITY', 'SALES') AND p.perm_code IN
      ('ledger','ledger:ledger','ledger:repository','ledger:notify','ledger:view','ledger:download')
ON CONFLICT DO NOTHING;

-- ---------- 10. 主键序列对齐 ----------
-- 第 8 节以显式 id 插入权限项, 不推进 sys_permission_id_seq; 不对齐则应用侧新增菜单
-- 递增到该段 id 时会主键冲突(与 40_permission_seq_fix.sql 同因同法)。
SELECT setval(
    pg_get_serial_sequence('sys_permission', 'id'),
    COALESCE((SELECT MAX(id) FROM sys_permission), 0) + 1,
    false
);
