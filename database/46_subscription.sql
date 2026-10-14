-- =============================================================================
-- HJ-PLM 增量 46: 资料分类订阅与报告推送（能力线 B）
-- 依据: PAND-198 需求 AC(B1-B6) + PAND-199 架构方案 §3.4-3.7/§5/§10
-- 内容: 分类维度/维度值/资料标签 + 订阅 + 报告 + 推送日志 + 编号序列
--       + 字典种子 + 全局维度种子 + 菜单权限与角色种子
-- 幂等: CREATE TABLE IF NOT EXISTS / ON CONFLICT DO NOTHING
--
-- 设计要点（与架构一致）:
--   1) 分类、订阅、报告是能力线 B 的独占表, 与能力线 A 的 ledger 表分离, 保持 A→B 串行不被交错修改。
--   2) 「资料」即 plm_ledger 一行(资料汇总处与证据台账同源), 分类标签挂在 ledger_id 上。
--   3) 站内通知复用能力线 A 的 plm_notification(45_ledger.sql), 本增量只用其 notify_type 的
--      SUB_ITEM / SUB_REPORT 取值, 不再建第二张通知表。
--   4) 报告正文 HTML + Excel 明细存 plm_file 引用(复用 file 底座, 不复制文件)。
--   5) 维度按部门隔离(dept_id), 全局维度 dept_id IS NULL 且 is_global=1 只读(AC-B2-6)。
-- =============================================================================

-- ---------- 1. 分类维度(AC-B1-1~B1-5、AC-B2) ----------
CREATE TABLE IF NOT EXISTS plm_category_dim (
    id          BIGSERIAL PRIMARY KEY,
    dept_id     BIGINT,                                     -- 归属部门; NULL = 全局维度(AC-B2-6)
    dim_code    VARCHAR(64),                                -- 维度编码(可选, 便于外部引用)
    dim_name    VARCHAR(20)  NOT NULL,                      -- 维度名称 <=20(AC-B1-1)
    is_global   SMALLINT     NOT NULL DEFAULT 0,            -- 1=全局预置, 任何部门只读(AC-B2-6)
    is_shared   SMALLINT     NOT NULL DEFAULT 0,            -- 1=共享, 他部门可选用不可编辑(AC-B2-4/5)
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',    -- ENABLED/DISABLED
    sort_order  INT          NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_category_dim IS '分类维度(部门隔离, AC-B1/B2); 全局维度 dept_id IS NULL 只读';
COMMENT ON COLUMN plm_category_dim.is_shared IS '共享后他部门可选该维度值, 但不可编辑其定义(AC-B2-5)';

CREATE INDEX IF NOT EXISTS idx_cat_dim_dept ON plm_category_dim (dept_id);
-- 维度名称在本部门内唯一(AC-B1-5)。PG 的 UNIQUE 把 NULL 视为互不相等, 故全局维度单列一条唯一索引。
CREATE UNIQUE INDEX IF NOT EXISTS uk_cat_dim_dept_name
    ON plm_category_dim (dept_id, dim_name) WHERE deleted = 0 AND dept_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cat_dim_global_name
    ON plm_category_dim (dim_name) WHERE deleted = 0 AND dept_id IS NULL;

-- ---------- 2. 分类维度值(AC-B1-1/B1-3/B1-4) ----------
CREATE TABLE IF NOT EXISTS plm_category_value (
    id          BIGSERIAL PRIMARY KEY,
    dim_id      BIGINT       NOT NULL,                      -- -> plm_category_dim.id
    value_code  VARCHAR(64),
    value_name  VARCHAR(30)  NOT NULL,                      -- 维度值 <=30(AC-B1-1)
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',    -- ENABLED/DISABLED(停用后不再出现在新选择项, AC-B1-4)
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_category_value IS '分类维度值(单维度上限 50, AC-B1-3 服务层计数)';
COMMENT ON COLUMN plm_category_value.status IS '停用后新分类选择项不出现, 但历史资料标签保留(AC-B1-4)';

CREATE INDEX IF NOT EXISTS idx_cat_value_dim ON plm_category_value (dim_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cat_value_name
    ON plm_category_value (dim_id, value_name) WHERE deleted = 0;

-- ---------- 3. 资料标签(资料 <-> 维度值, AC-B1-6/B1-7) ----------
CREATE TABLE IF NOT EXISTS plm_ledger_category (
    id          BIGSERIAL PRIMARY KEY,
    ledger_id   BIGINT       NOT NULL,                      -- -> plm_ledger.id(资料 = 台账行)
    dim_id      BIGINT       NOT NULL,                      -- -> plm_category_dim.id
    value_id    BIGINT       NOT NULL,                      -- -> plm_category_value.id
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_ledger_category IS '资料分类标签; 一条资料可多维度并存, 同一维度只取一个值(AC-B1-6)';

CREATE INDEX IF NOT EXISTS idx_ledger_cat_ledger ON plm_ledger_category (ledger_id);
CREATE INDEX IF NOT EXISTS idx_ledger_cat_value  ON plm_ledger_category (value_id);
CREATE INDEX IF NOT EXISTS idx_ledger_cat_dim    ON plm_ledger_category (dim_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_cat
    ON plm_ledger_category (ledger_id, dim_id) WHERE deleted = 0;

-- ---------- 4. 订阅(单条 ITEM / 周期 PERIOD, AC-B3/B4) ----------
CREATE TABLE IF NOT EXISTS plm_subscription (
    id            BIGSERIAL PRIMARY KEY,
    sub_no        VARCHAR(32)  NOT NULL UNIQUE,             -- 订阅编号 SUB-yyyyMMdd-NNNN(sys_sequence)
    user_id       BIGINT       NOT NULL,                    -- 订阅人
    type          VARCHAR(8)   NOT NULL,                    -- ITEM 单条(AC-B3) / PERIOD 周期(AC-B4)
    name          VARCHAR(64),                              -- 订阅名称(列表展示, AC-B4-9)
    ledger_id     BIGINT,                                   -- 单条订阅指向的资料(AC-B3-1)
    filter_json   JSONB,                                    -- 周期订阅分类过滤 {dims:[{dimId,valueIds:[...]}]}(AC-B4-3)
    period        VARCHAR(8),                               -- DAY/WEEK/MONTH(AC-B4-1)
    weekday       SMALLINT,                                 -- 按周: 1-7(周一=1)
    monthday      SMALLINT,                                 -- 按月: 1-28
    push_time     TIME         NOT NULL DEFAULT '08:00',    -- 推送时点(AC-B5-1)
    status        VARCHAR(8)   NOT NULL DEFAULT 'ENABLED',  -- ENABLED/PAUSED/DELETED(AC-B4-6/8)
    next_run_at   TIMESTAMPTZ,                              -- 下次生成时点(调度游标, AC-B4-3)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_subscription IS '订阅(单条 + 周期统一表); 周期订阅上限 20 条/人(AC-B4-9 服务层计数)';
COMMENT ON COLUMN plm_subscription.next_run_at IS '周期订阅下一生成时点; 修改周期/时点后前移到下一完整周期(AC-B4-7)';

CREATE INDEX IF NOT EXISTS idx_subscription_user   ON plm_subscription (user_id, status);
CREATE INDEX IF NOT EXISTS idx_subscription_ledger ON plm_subscription (ledger_id);
-- 调度扫描: status=ENABLED AND next_run_at <= now()(AC-B5-1 分钟级扫描)
CREATE INDEX IF NOT EXISTS idx_subscription_due    ON plm_subscription (next_run_at) WHERE deleted = 0;
-- 同一用户对同一资料只保留一条有效单条订阅(AC-B3-6 不同用户互不影响)
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_item
    ON plm_subscription (user_id, ledger_id) WHERE deleted = 0 AND type = 'ITEM';

-- ---------- 5. 报告(AC-B5) ----------
CREATE TABLE IF NOT EXISTS plm_report (
    id              BIGSERIAL PRIMARY KEY,
    report_no       VARCHAR(32) NOT NULL UNIQUE,            -- 报告编号 RPT-yyyyMMdd-NNNN
    subscription_id BIGINT,                                 -- -> plm_subscription.id
    user_id         BIGINT      NOT NULL,                   -- 报告归属人
    type            VARCHAR(8)  NOT NULL,                   -- ITEM/PERIOD(随订阅)
    sub_name        VARCHAR(64),                            -- 订阅名称快照(订阅删除后仍可回溯, AC-B4-8)
    period_start    TIMESTAMPTZ,                            -- 周期区间起(单条订阅为触发时刻)
    period_end      TIMESTAMPTZ,                            -- 周期区间止
    item_count      INT         NOT NULL DEFAULT 0,         -- 报告内资料条数(去重后, AC-B4-4)
    content_json    JSONB,                                  -- 资料条目清单(含指向资料汇总处的链接, AC-B5-2)
    html_body       TEXT,                                   -- HTML 正文清单
    excel_file_id   BIGINT,                                 -- 预留: Excel 明细改由 content_json 快照按需生成, 不落盘故留 NULL(AC-B5-7)
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/GENERATED/PUSHED/FAILED
    retry_count     INT         NOT NULL DEFAULT 0,         -- 生成失败重试次数(AC-B5-5)
    error           VARCHAR(1000),
    generated_at    TIMESTAMPTZ,
    pushed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         SMALLINT    NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_report IS '订阅报告; 最近 12 期/人可回溯下载(AC-B5-7 服务层限制)';
COMMENT ON COLUMN plm_report.html_body IS 'HTML 清单正文(站内通知正文 + 邮件正文共用)';

CREATE INDEX IF NOT EXISTS idx_report_user ON plm_report (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_sub  ON plm_report (subscription_id);

-- ---------- 6. 报告推送日志(AC-B5-6) ----------
CREATE TABLE IF NOT EXISTS plm_report_delivery_log (
    id           BIGSERIAL PRIMARY KEY,
    report_id    BIGINT      NOT NULL,                      -- -> plm_report.id
    channel      VARCHAR(16) NOT NULL,                      -- INBOX 站内 / EMAIL 邮件
    result       VARCHAR(16) NOT NULL,                      -- SUCCESS/FAILED/SKIPPED(渠道未启用)
    target       VARCHAR(256),                              -- 邮件渠道记录收件地址
    error        VARCHAR(1000),
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_report_delivery_log IS '报告推送日志: 订阅/周期区间/生成时间/渠道/结果(AC-B5-6), 成功失败均记';

CREATE INDEX IF NOT EXISTS idx_report_delivery_report ON plm_report_delivery_log (report_id);

-- ---------- 7. 编号序列: 复用 sys_sequence ----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator)
VALUES ('SUBSCRIPTION_NO', 'SUB-', 'yyyyMMdd', 4, '-')
ON CONFLICT (seq_key) DO NOTHING;

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator)
VALUES ('REPORT_NO', 'RPT-', 'yyyyMMdd', 4, '-')
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 8. 字典种子: 订阅周期 / 订阅类型 / 订阅状态 / 报告状态 ----------
-- sys_dict 无唯一键, 幂等靠 WHERE NOT EXISTS(与 45_ledger.sql 同口径)
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('subscription_period', '按日', 'DAY',   1),
('subscription_period', '按周', 'WEEK',  2),
('subscription_period', '按月', 'MONTH', 3)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('subscription_type', '单条订阅', 'ITEM',   1),
('subscription_type', '周期订阅', 'PERIOD', 2)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('subscription_status', '启用', 'ENABLED', 1),
('subscription_status', '暂停', 'PAUSED',  2),
('subscription_status', '已删除', 'DELETED', 3)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_label, v.dict_value, v.sort_order FROM (VALUES
('report_status', '待生成', 'PENDING',   1),
('report_status', '已生成', 'GENERATED', 2),
('report_status', '已推送', 'PUSHED',    3),
('report_status', '生成失败', 'FAILED',  4)
) AS v(dict_type, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_value = v.dict_value);

-- ---------- 9. 全局维度种子: 「资料类型」(AC-B2-6 对全部部门只读可见) ----------
INSERT INTO plm_category_dim (dept_id, dim_code, dim_name, is_global, is_shared, status, sort_order)
SELECT NULL, 'GLOBAL_LEDGER_TYPE', '资料类型', 1, 1, 'ENABLED', 1
WHERE NOT EXISTS (
    SELECT 1 FROM plm_category_dim WHERE dim_name = '资料类型' AND dept_id IS NULL AND deleted = 0);

-- 全局维度的取值与 45_ledger.sql 的 ledger_type 字典同口径, 保证历史资料可直接打标。
INSERT INTO plm_category_value (dim_id, value_code, value_name, status, sort_order)
SELECT d.id, v.value_code, v.value_name, 'ENABLED', v.sort_order
FROM plm_category_dim d
JOIN (VALUES
    ('DESIGN_INPUT',   '设计输入', 1),
    ('REVIEW_RECORD',  '评审记录', 2),
    ('TEST_REPORT',    '试验报告', 3),
    ('INSPECT_REPORT', '检验报告', 4),
    ('CHANGE_PROOF',   '变更凭证', 5),
    ('BORROW_RECORD',  '借阅记录', 6)
) AS v(value_code, value_name, sort_order) ON TRUE
WHERE d.dim_name = '资料类型' AND d.dept_id IS NULL AND d.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM plm_category_value cv
      WHERE cv.dim_id = d.id AND cv.value_name = v.value_name AND cv.deleted = 0);

-- ---------- 10. 菜单权限种子(复用 sys_permission) ----------
-- 一级菜单 111「订阅与报告」+ 三页: 分类管理 / 我的订阅 / 我的报告; 按钮权限 1115..1120。
-- 注: 111 及 111x 段在既有种子中未被占用(110 = 外协, 109 = 台账/档案)。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
VALUES
(111,   0,   'subscription',          '订阅与报告', 1, '/subscription',          NULL,                      'Bell',     7),
(1110,  111, 'subscription:category', '分类管理',   1, '/subscription/category', 'subscription/category',   'PriceTag', 1),
(1111,  111, 'subscription:sub',      '我的订阅',   1, '/subscription/list',     'subscription/subscription','Star',    2),
(1112,  111, 'subscription:report',   '我的报告',   1, '/subscription/report',   'subscription/report',     'Document', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
VALUES
(1115, 111, 'subscription:view',     '查看订阅',     2, 1),
(1116, 111, 'subscription:manage',   '管理订阅',     2, 2),
(1117, 111, 'category:view',         '查看分类维度', 2, 3),
(1118, 111, 'category:manage',       '管理部门分类', 2, 4),
(1119, 111, 'report:view',           '查看报告',     2, 5),
(1120, 111, 'report:download',       '下载报告',     2, 6)
ON CONFLICT (id) DO NOTHING;

-- ---------- 11. 角色种子 ----------
-- data_scope: 1=全部 2=本部门 3=本人 (sys_role.data_scope, 见 01_schema.sql)
INSERT INTO sys_role (role_code, role_name, role_level, data_scope, builtin, remark)
VALUES
('CATEGORY_ADMIN', '部门分类管理员', 4, 2, 0, '管理本部门分类维度与分类值(AC-B1/B2); 具备订阅与报告权限')
ON CONFLICT (role_code) DO NOTHING;

-- ADMIN: 全量权限补授(02 号种子的 ADMIN 授权是初始化快照, 之后新增的权限不在其中)
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

-- 台账管理员: 订阅与报告 + 分类查看(分类维护归部门分类管理员)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'LEDGER_ADMIN' AND p.perm_code IN
      ('subscription','subscription:category','subscription:sub','subscription:report',
       'subscription:view','subscription:manage','category:view','report:view','report:download')
ON CONFLICT DO NOTHING;

-- 台账录入人 / 台账查看人 / 研发工程师: 订阅与报告 + 分类查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('LEDGER_EDITOR', 'LEDGER_VIEWER', 'ENGINEER') AND p.perm_code IN
      ('subscription','subscription:category','subscription:sub','subscription:report',
       'subscription:view','subscription:manage','category:view','report:view','report:download')
ON CONFLICT DO NOTHING;

-- 品质 / 销售: 订阅与报告 + 分类查看(对齐 AC-B6 查看人职责)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('QUALITY', 'SALES') AND p.perm_code IN
      ('subscription','subscription:category','subscription:sub','subscription:report',
       'subscription:view','subscription:manage','category:view','report:view','report:download')
ON CONFLICT DO NOTHING;

-- ---------- 12. 主键序列对齐 ----------
-- 第 10 节以显式 id 插入权限项, 不推进 sys_permission_id_seq; 不对齐则应用侧新增菜单
-- 递增到该段 id 时会主键冲突(与 40_permission_seq_fix.sql 同因同法)。
SELECT setval(
    pg_get_serial_sequence('sys_permission', 'id'),
    COALESCE((SELECT MAX(id) FROM sys_permission), 0) + 1,
    false
);
