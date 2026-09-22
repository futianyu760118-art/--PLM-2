-- =====================================================================
-- 恒剑光电 PLM 中台 V4.0 数据库建表脚本
-- 数据库: PostgreSQL 14+
-- 文档编号: HJ-PLM-V4.0-20260704
-- 说明: 以 PartNo(物料料号) 为全局唯一主键的全链路设计
-- =====================================================================

-- 创建枚举类型
-- 物料类型
CREATE TYPE material_type_enum AS ENUM (
    'FINISHED',     -- 成品
    'SEMI',         -- 半成品
    'PLASTIC',      -- 塑胶件
    'HARDWARE',     -- 五金件
    'STANDARD'      -- 标准件
);

-- 物料状态: 草稿/评审中/正式发布/量产在用/变更中/作废/停产封存
CREATE TYPE material_status_enum AS ENUM (
    'DRAFT',        -- 草稿
    'REVIEWING',    -- 评审中
    'RELEASED',     -- 正式发布
    'IN_PRODUCTION',-- 量产在用
    'CHANGING',     -- 变更中
    'OBSOLETE',     -- 作废
    'SEALED'        -- 停产封存
);

-- ECN 变更类型
CREATE TYPE ecn_change_type_enum AS ENUM (
    'STRUCTURE',    -- 结构变更
    'MOLD',         -- 模具变更
    'PROCESS',      -- 工艺变更
    'BOM',          -- BOM变更
    'DIMENSION'     -- 尺寸变更
);

-- ECN 审批状态: 草稿/待一审/待二审/审批通过/审批驳回/已生效/已作废
CREATE TYPE ecn_status_enum AS ENUM (
    'DRAFT',        -- 草稿
    'PENDING_L1',   -- 待一审(研发主管)
    'PENDING_L2',   -- 待二审(供应链总监)
    'APPROVED',     -- 审批通过
    'REJECTED',     -- 审批驳回
    'EFFECTIVE',    -- 已生效
    'VOID'          -- 已作废
);

-- 档案生命周期状态
CREATE TYPE archive_status_enum AS ENUM (
    'DRAFT',        -- 新建草稿
    'REVIEWING',    -- 内部评审
    'RELEASED',     -- 正式发布
    'CHANGING',     -- 变更迭代
    'OBSOLETE',     -- 作废
    'SEALED'        -- 封存归档
);

-- 文件可见范围
CREATE TYPE file_visibility_enum AS ENUM (
    'INTRANET',     -- 仅内网
    'OUTSOURCE',    -- 外协可见
    'EXTERNAL',     -- 外部客户可见
    'PUBLIC'        -- 全公开
);

-- =====================================================================
-- 1. 系统基础 RBAC 表
-- =====================================================================

-- 1.1 部门表
CREATE TABLE sys_department (
    id BIGSERIAL PRIMARY KEY,
    dept_code VARCHAR(50) UNIQUE NOT NULL,
    dept_name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES sys_department(id),
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_department IS '部门表';

-- 1.2 用户表
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    employee_no VARCHAR(32) UNIQUE,
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar VARCHAR(255),
    dept_id BIGINT REFERENCES sys_department(id),
    status SMALLINT DEFAULT 1,  -- 1启用 0禁用
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(64),
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
COMMENT ON TABLE sys_user IS '系统用户表';

-- 1.3 角色表 (固定六大角色)
CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) UNIQUE NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    role_level SMALLINT DEFAULT 0,
    data_scope SMALLINT DEFAULT 1,  -- 1全部 2本部门 3本人
    builtin SMALLINT DEFAULT 0,     -- 1内置(不可删)
    status SMALLINT DEFAULT 1,
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
COMMENT ON TABLE sys_role IS '系统角色表(固定六大角色)';

-- 1.4 权限/菜单表
CREATE TABLE sys_permission (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    perm_code VARCHAR(100) UNIQUE NOT NULL,
    perm_name VARCHAR(100) NOT NULL,
    perm_type SMALLINT NOT NULL,   -- 1菜单 2按钮 3接口
    path VARCHAR(255),
    component VARCHAR(255),
    icon VARCHAR(100),
    sort_order INT DEFAULT 0,
    visible SMALLINT DEFAULT 1,
    status SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_permission IS '权限菜单表(菜单/按钮/接口)';

-- 1.5 用户-角色关联
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 1.6 角色-权限关联
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- =====================================================================
-- 2. 物料主数据 (全局唯一主键 PartNo)
-- =====================================================================

CREATE TABLE plm_material (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) UNIQUE NOT NULL,          -- 物料料号(全局唯一)
    material_name VARCHAR(200) NOT NULL,          -- 物料名称
    material_type material_type_enum NOT NULL,    -- 物料类型
    material_texture VARCHAR(100),                -- 材质
    color VARCHAR(64),                            -- 颜色
    specification VARCHAR(255),                   -- 规格尺寸
    product_series VARCHAR(100),                  -- 产品系列
    project_no VARCHAR(64),                       -- 归属项目
    supplier_code VARCHAR(64),                    -- 供应商编码
    supplier_name VARCHAR(200),                   -- 供应商名称
    status material_status_enum DEFAULT 'DRAFT',  -- 物料状态
    version_no VARCHAR(32) DEFAULT 'V1.0',        -- 版本号
    make_type SMALLINT DEFAULT 0,                 -- 0自制 1外购
    unit VARCHAR(32),                             -- 单位
    lifecycle_status archive_status_enum DEFAULT 'DRAFT', -- 档案生命周期
    created_by VARCHAR(64),                       -- 创建人
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remark TEXT,
    deleted SMALLINT DEFAULT 0                    -- 软删除(只用于误删,正式档案禁止删除)
);
CREATE INDEX idx_material_name ON plm_material(material_name);
CREATE INDEX idx_material_type ON plm_material(material_type);
CREATE INDEX idx_material_series ON plm_material(product_series);
CREATE INDEX idx_material_status ON plm_material(status);
COMMENT ON TABLE plm_material IS '物料主数据表(全系统根节点)';
COMMENT ON COLUMN plm_material.part_no IS '物料料号-全局唯一主键';

-- 物料历史版本归档
CREATE TABLE plm_material_version (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL REFERENCES plm_material(id),
    part_no VARCHAR(64) NOT NULL,
    version_no VARCHAR(32) NOT NULL,
    snapshot JSONB NOT NULL,                       -- 完整快照
    change_reason VARCHAR(500),
    ecn_no VARCHAR(64),                            -- 关联ECN单号
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (material_id, version_no)
);
COMMENT ON TABLE plm_material_version IS '物料历史版本归档';

-- =====================================================================
-- 3. ECN 工程变更管控中心
-- =====================================================================

CREATE TABLE plm_ecn (
    id BIGSERIAL PRIMARY KEY,
    ecn_no VARCHAR(64) UNIQUE NOT NULL,            -- ECN单号
    part_no VARCHAR(64) NOT NULL,                  -- 关联物料料号
    material_id BIGINT,
    material_name VARCHAR(200),
    change_type ecn_change_type_enum NOT NULL,     -- 变更类型
    version_before VARCHAR(32),                    -- 变更前版本
    version_after VARCHAR(32),                     -- 变更后版本
    change_location TEXT,                          -- 变更位置
    change_reason TEXT NOT NULL,                   -- 变更详细原因
    mold_cost DECIMAL(12,2) DEFAULT 0,             -- 改模成本
    impact_scope VARCHAR(500),                     -- 影响范围
    trial_impact VARCHAR(500),                     -- 试制影响
    mass_impact VARCHAR(500),                      -- 量产影响
    status ecn_status_enum DEFAULT 'DRAFT',        -- 审批状态
    applicant VARCHAR(64) NOT NULL,                -- 申请人
    apply_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,-- 申请时间
    review_l1_by VARCHAR(64),                      -- 一审人(研发主管)
    review_l1_time TIMESTAMP,
    review_l1_comment VARCHAR(500),
    review_l2_by VARCHAR(64),                      -- 二审人(供应链总监)
    review_l2_time TIMESTAMP,
    review_l2_comment VARCHAR(500),
    final_comment VARCHAR(500),                    -- 终审意见
    effective_time TIMESTAMP,                      -- 生效时间
    void_time TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (part_no) REFERENCES plm_material(part_no)
);
CREATE INDEX idx_ecn_part_no ON plm_ecn(part_no);
CREATE INDEX idx_ecn_status ON plm_ecn(status);
CREATE INDEX idx_ecn_applicant ON plm_ecn(applicant);
COMMENT ON TABLE plm_ecn IS 'ECN工程变更单(三级审批)';
-- ECN 变更前后附图
CREATE TABLE plm_ecn_attachment (
    id BIGSERIAL PRIMARY KEY,
    ecn_id BIGINT NOT NULL REFERENCES plm_ecn(id) ON DELETE CASCADE,
    attach_type SMALLINT NOT NULL,                 -- 1变更前附图 2变更后附图
    file_id BIGINT NOT NULL,
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_ecn_attachment IS 'ECN变更前后附图';

-- ECN 审批流转记录(完整留痕)
CREATE TABLE plm_ecn_flow_log (
    id BIGSERIAL PRIMARY KEY,
    ecn_id BIGINT NOT NULL REFERENCES plm_ecn(id) ON DELETE CASCADE,
    step SMALLINT NOT NULL,                        -- 0提交 1一审 2二审 3生效
    action VARCHAR(32) NOT NULL,                   -- submit/approve/reject/effect
    operator VARCHAR(64) NOT NULL,
    operator_role VARCHAR(64),
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    comment VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_ecn_flow_log IS 'ECN审批流转记录';

-- =====================================================================
-- 4. BOM 多级爆炸 (树形结构, 以料号关联)
-- =====================================================================

-- BOM 主表(单套BOM版本)
CREATE TABLE plm_bom (
    id BIGSERIAL PRIMARY KEY,
    bom_no VARCHAR(64) UNIQUE NOT NULL,            -- BOM编号
    root_part_no VARCHAR(64) NOT NULL,             -- 顶级成品料号
    material_id BIGINT NOT NULL REFERENCES plm_material(id),
    version_no VARCHAR(32) DEFAULT 'V1.0',         -- BOM版本
    status archive_status_enum DEFAULT 'DRAFT',    -- 状态
    ecn_no VARCHAR(64),                            -- 关联ECN(最近变更)
    source SMALLINT DEFAULT 0,                     -- 0手工 1自动3D解析生成
    remark VARCHAR(500),
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (root_part_no, version_no)
);
CREATE INDEX idx_bom_root_part ON plm_bom(root_part_no);
COMMENT ON TABLE plm_bom IS 'BOM主表(按版本管理)';

-- BOM 明细行(无限级, 邻接表 + 物化路径)
CREATE TABLE plm_bom_item (
    id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL REFERENCES plm_bom(id) ON DELETE CASCADE,
    parent_item_id BIGINT DEFAULT 0,               -- 父件明细ID(0=顶级)
    path LTREE,                                    -- 物化路径,加速树查询
    level_no INT DEFAULT 1,                        -- 层级
    parent_part_no VARCHAR(64),                    -- 父件料号
    part_no VARCHAR(64) NOT NULL,                  -- 子件料号
    material_id BIGINT NOT NULL REFERENCES plm_material(id),
    part_name VARCHAR(200),                        -- 零件名称
    quantity NUMERIC(14,4) DEFAULT 1,              -- 数量
    material_texture VARCHAR(100),                 -- 材质
    specification VARCHAR(255),                    -- 规格
    unit VARCHAR(32),                              -- 单位
    make_type SMALLINT DEFAULT 0,                  -- 0自制 1外购
    version_no VARCHAR(32),                        -- 版本号
    model3d_file_id BIGINT,                        -- 对应3D文件
    drawing_file_id BIGINT,                        -- 对应加工图纸
    sort_order INT DEFAULT 0,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_bom_item_bom ON plm_bom_item(bom_id);
CREATE INDEX idx_bom_item_parent ON plm_bom_item(parent_item_id);
CREATE INDEX idx_bom_item_part ON plm_bom_item(part_no);
CREATE INDEX idx_bom_item_path ON plm_bom_item USING GIST (path);
COMMENT ON TABLE plm_bom_item IS 'BOM明细行(无限级树形)';
COMMENT ON COLUMN plm_bom_item.path IS '物化路径, 树形加速查询';

-- BOM 版本对比归档
CREATE TABLE plm_bom_version (
    id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL REFERENCES plm_bom(id),
    version_no VARCHAR(32) NOT NULL,
    snapshot JSONB NOT NULL,
    ecn_no VARCHAR(64),
    change_reason VARCHAR(500),
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bom_id, version_no)
);
COMMENT ON TABLE plm_bom_version IS 'BOM历史版本归档';

-- =====================================================================
-- 5. 文件附件管理 (所有文件挂载料号下)
-- =====================================================================

CREATE TABLE plm_file (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,               -- 原始文件名
    file_path VARCHAR(500) NOT NULL,               -- 存储路径
    file_url VARCHAR(500),                         -- 访问URL
    file_ext VARCHAR(20),                          -- 扩展名
    file_size BIGINT,                              -- 字节
    file_type VARCHAR(50),                         -- 业务类型: 3d/drawing/ecn/report
    md5_hash VARCHAR(64),                          -- MD5去重
    visibility file_visibility_enum DEFAULT 'INTRANET', -- 可见范围
    has_watermark SMALLINT DEFAULT 0,              -- 是否带水印版本
    expire_at TIMESTAMP,                           -- 外协有效期
    part_no VARCHAR(64),                           -- 关联料号
    version_no VARCHAR(32),                        -- 关联版本
    obsolete SMALLINT DEFAULT 0,                   -- 是否作废(加盖水印锁定)
    uploaded_by VARCHAR(64),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_file_part_no ON plm_file(part_no);
CREATE INDEX idx_file_type ON plm_file(file_type);
COMMENT ON TABLE plm_file IS '文件附件表';

-- =====================================================================
-- 6. 全操作日志溯源引擎
-- =====================================================================

CREATE TABLE sys_operation_log (
    id BIGSERIAL PRIMARY KEY,
    operator VARCHAR(64) NOT NULL,                 -- 操作人
    user_id BIGINT,
    username VARCHAR(64),
    ip VARCHAR(64),                                -- IP地址
    device VARCHAR(255),                           -- 设备信息
    operation VARCHAR(64) NOT NULL,                -- 操作类型
    method VARCHAR(255),                           -- 调用方法
    params TEXT,                                   -- 请求参数
    result SMALLINT DEFAULT 1,                     -- 1成功 0失败
    error_msg TEXT,
    part_no VARCHAR(64),                           -- 关联料号
    file_version VARCHAR(32),                      -- 文件版本
    cost_ms INT,                                   -- 耗时(毫秒)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_oplog_operator ON sys_operation_log(operator);
CREATE INDEX idx_oplog_operation ON sys_operation_log(operation);
CREATE INDEX idx_oplog_part_no ON sys_operation_log(part_no);
CREATE INDEX idx_oplog_created ON sys_operation_log(created_at);
COMMENT ON TABLE sys_operation_log IS '全操作日志溯源表';

-- =====================================================================
-- 7. 字典表 (枚举展示用)
-- =====================================================================

CREATE TABLE sys_dict (
    id BIGSERIAL PRIMARY KEY,
    dict_type VARCHAR(64) NOT NULL,
    dict_label VARCHAR(128) NOT NULL,
    dict_value VARCHAR(64) NOT NULL,
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dict_type ON sys_dict(dict_type);
COMMENT ON TABLE sys_dict IS '系统字典表';

-- =====================================================================
-- 8. 序列号生成器 (料号/ECN/BOM 编号规则)
-- =====================================================================

CREATE TABLE sys_sequence (
    id BIGSERIAL PRIMARY KEY,
    seq_key VARCHAR(64) UNIQUE NOT NULL,
    prefix VARCHAR(16),
    current_val BIGINT DEFAULT 0,
    date_pattern VARCHAR(16) DEFAULT 'yyyyMMdd',
    length INT DEFAULT 4,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_sequence IS '编号序列生成器';
