-- =====================================================================
-- 恒剑光电 PLM V4.0 扩展表 (模块4-8 + 模具资产 + 试模履历 + 注塑工艺)
-- =====================================================================

-- ---------- 档案固定目录树 (单款产品自动生成的标准目录) ----------
CREATE TABLE plm_archive_tree (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) NOT NULL,                   -- 关联料号
    node_code VARCHAR(64) NOT NULL,                 -- 目录编码(固定)
    node_name VARCHAR(128) NOT NULL,                -- 目录名称
    parent_id BIGINT DEFAULT 0,                     -- 父节点
    level_no INT DEFAULT 1,                         -- 层级
    sort_order INT DEFAULT 0,
    is_leaf SMALLINT DEFAULT 0,                     -- 是否叶子目录
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (part_no, node_code)
);
COMMENT ON TABLE plm_archive_tree IS '产品固定档案目录树(17层标准目录)';

-- ---------- 文件-目录关联 (文件挂载到固定目录节点) ----------
CREATE TABLE plm_archive_file (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) NOT NULL,
    archive_node_id BIGINT NOT NULL REFERENCES plm_archive_tree(id),
    file_id BIGINT NOT NULL REFERENCES plm_file(id),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_archive_file IS '档案目录-文件关联';

-- ---------- 模块4: IQC/IPQC 品质检验标准 ----------
CREATE TYPE inspection_category_enum AS ENUM (
    'DIMENSION',    -- 尺寸检验
    'APPEARANCE',   -- 外观检验
    'ASSEMBLY',     -- 装配检验
    'FUNCTION',     -- 功能检验
    'WATERPROOF',   -- 防水结构检验
    'INCOMING'      -- 来料检验
);

CREATE TABLE plm_inspection_standard (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) NOT NULL,                   -- 关联料号
    material_id BIGINT,
    category inspection_category_enum NOT NULL,     -- 检验分类
    item_name VARCHAR(200) NOT NULL,                -- 检验项目
    standard_value VARCHAR(200),                    -- 标准值
    tolerance_range VARCHAR(200),                   -- 公差范围
    tool VARCHAR(200),                              -- 检验工具
    method VARCHAR(500),                            -- 检验方法
    criteria VARCHAR(500),                          -- 判定标准
    defect_def VARCHAR(500),                        -- 不良定义
    model3d_position VARCHAR(500),                  -- 参考3D位置
    drawing_version VARCHAR(32),                    -- 参考图纸版本
    status archive_status_enum DEFAULT 'DRAFT',     -- 生效状态
    version_no VARCHAR(32) DEFAULT 'V1.0',
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_insp_part_no ON plm_inspection_standard(part_no);
CREATE INDEX idx_insp_category ON plm_inspection_standard(category);
COMMENT ON TABLE plm_inspection_standard IS 'IQC/IPQC品质检验标准';

-- ---------- 模块5: 外协发图审批 ----------
CREATE TYPE outsource_status_enum AS ENUM (
    'DRAFT',        -- 草稿
    'PENDING',      -- 待审批
    'APPROVED',     -- 审批通过
    'REJECTED',     -- 审批驳回
    'EXPIRED',      -- 已过期
    'VOID'          -- 已作废
);

CREATE TABLE plm_outsource_request (
    id BIGSERIAL PRIMARY KEY,
    request_no VARCHAR(64) UNIQUE NOT NULL,         -- 申请单号
    outsource_company VARCHAR(200) NOT NULL,        -- 外协单位
    contact_person VARCHAR(100),                    -- 联系人
    purpose VARCHAR(500),                           -- 用途
    drawing_type VARCHAR(100),                      -- 图纸类型
    validity_days INT DEFAULT 7,                    -- 有效期(7/15/30)
    expire_at TIMESTAMP,                            -- 到期时间
    description VARCHAR(500),                       -- 申请说明
    applicant VARCHAR(64) NOT NULL,                 -- 申请人
    status outsource_status_enum DEFAULT 'DRAFT',   -- 审批状态
    approver VARCHAR(64),                           -- 审批人
    approve_time TIMESTAMP,
    approve_comment VARCHAR(500),
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_outsource_company ON plm_outsource_request(outsource_company);
CREATE INDEX idx_outsource_status ON plm_outsource_request(status);
COMMENT ON TABLE plm_outsource_request IS '外协发图审批申请';

-- 外协申请关联的文件包
CREATE TABLE plm_outsource_file (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES plm_outsource_request(id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES plm_file(id),
    watermarked_file_id BIGINT,                     -- 带水印版本文件ID
    download_count INT DEFAULT 0,
    last_download_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_outsource_file IS '外协申请关联文件包';

-- 外协下载记录
CREATE TABLE plm_outsource_download_log (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    part_no VARCHAR(64),
    downloader VARCHAR(64),
    download_ip VARCHAR(64),
    device VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_outsource_download_log IS '外协下载记录(全留痕)';

-- ---------- 模块I: 模具固定资产台账 ----------
CREATE TYPE mold_status_enum AS ENUM (
    'IN_DESIGN',        -- 设计中
    'IN_MACHINING',     -- 加工中
    'TRIAL',            -- 试模中
    'IN_PRODUCTION',    -- 量产中
    'MAINTENANCE',      -- 维修保养
    'OBSOLETE',         -- 报废
    'SEALED'            -- 封存
);

CREATE TABLE plm_mold (
    id BIGSERIAL PRIMARY KEY,
    mold_no VARCHAR(64) UNIQUE NOT NULL,            -- 模具编号
    part_no VARCHAR(64) NOT NULL,                   -- 对应产品料号
    mold_name VARCHAR(200),                         -- 模具名称
    open_date DATE,                                 -- 开模日期
    cavity_count INT DEFAULT 1,                     -- 模腔数量
    accumulate_shots BIGINT DEFAULT 0,              -- 累计生产啤数
    maintenance_cycle INT DEFAULT 10000,            -- 标准保养周期(啤)
    last_maintenance_date DATE,                     -- 上次保养日期
    polish_record TEXT,                             -- 抛光记录
    insert_replace_record TEXT,                     -- 镶件更换记录
    repair_record TEXT,                             -- 大修维修记录
    scrap_date DATE,                                -- 报废日期
    status mold_status_enum DEFAULT 'IN_DESIGN',    -- 模具状态
    asset_value DECIMAL(14,2),                      -- 模具资产价值
    remark TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_mold_part_no ON plm_mold(part_no);
CREATE INDEX idx_mold_status ON plm_mold(status);
COMMENT ON TABLE plm_mold IS '模具固定资产台账';

-- 模块G: 试模/修模履历
CREATE TABLE plm_mold_trial_log (
    id BIGSERIAL PRIMARY KEY,
    mold_no VARCHAR(64) NOT NULL,                   -- 模具编号
    part_no VARCHAR(64),                            -- 产品料号
    trial_time TIMESTAMP NOT NULL,                  -- 试模时间
    trial_count INT DEFAULT 1,                      -- 第几次试模
    defect_phenomenon TEXT,                         -- 试模不良现象
    repair_position VARCHAR(500),                   -- 修模对应位置
    modify_data VARCHAR(500),                       -- 尺寸修改数据
    solution TEXT,                                  -- 整改处理方案
    handler VARCHAR(64),                            -- 处理人
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_trial_mold ON plm_mold_trial_log(mold_no);
COMMENT ON TABLE plm_mold_trial_log IS '试模修模履历台账';

-- 模块H: 注塑成型SOP工艺卡
CREATE TABLE plm_injection_sop (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) NOT NULL,                   -- 关联料号
    mold_no VARCHAR(64),                            -- 模具编号
    material_spec VARCHAR(100),                     -- 塑胶材质(PA/PC/ABS等)
    melt_temp DECIMAL(6,1),                         -- 料温
    mold_temp DECIMAL(6,1),                         -- 模温
    injection_pressure DECIMAL(8,1),                -- 射胶压力
    packing_pressure DECIMAL(8,1),                  -- 保压参数
    injection_speed DECIMAL(8,1),                   -- 射速
    cooling_time DECIMAL(6,1),                      -- 冷却时间
    shrinkage_comp DECIMAL(6,3),                    -- 缩水补偿值
    sprue_ratio DECIMAL(6,2),                       -- 水口比例
    defect_solutions TEXT,                          -- 不良问题改善对策库
    status archive_status_enum DEFAULT 'DRAFT',
    version_no VARCHAR(32) DEFAULT 'V1.0',
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_sop_part_no ON plm_injection_sop(part_no);
COMMENT ON TABLE plm_injection_sop IS '注塑成型SOP工艺卡';

-- ---------- 3D 模型库 (模块A/B/D) ----------
CREATE TYPE model3d_type_enum AS ENUM (
    'APPEARANCE',       -- 模块A: 外观3D
    'STRUCTURE',        -- 模块B: 产品结构3D
    'MOLD_FLOW',        -- 模块C: 模流(报告)
    'MOLD',             -- 模块D: 模具拆模3D
    'EXPLODE'           -- 爆炸图
);

CREATE TABLE plm_model3d (
    id BIGSERIAL PRIMARY KEY,
    part_no VARCHAR(64) NOT NULL,                   -- 关联料号
    model_name VARCHAR(200),                        -- 模型名称
    model_type model3d_type_enum NOT NULL,          -- 模型类型
    source_format VARCHAR(20),                      -- 原始格式(STEP/OBJ...)
    intranet_file_id BIGINT,                        -- 内网高精度文件
    extranet_file_id BIGINT,                        -- 外网脱敏GLB文件
    thumbnail VARCHAR(500),                         -- 缩略图
    explode_json TEXT,                              -- 爆炸BOM解析数据
    version_no VARCHAR(32) DEFAULT 'V1.0',
    ecn_no VARCHAR(64),                             -- 关联ECN
    status archive_status_enum DEFAULT 'DRAFT',
    source SMALLINT DEFAULT 0,                      -- 0上传 1AI生成
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_model3d_part_no ON plm_model3d(part_no);
CREATE INDEX idx_model3d_type ON plm_model3d(model_type);
COMMENT ON TABLE plm_model3d IS '3D模型库(外观/结构/模具/爆炸)';

-- ---------- 字典补充 ----------
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('inspection_category', '尺寸检验', 'DIMENSION', 1),
('inspection_category', '外观检验', 'APPEARANCE', 2),
('inspection_category', '装配检验', 'ASSEMBLY', 3),
('inspection_category', '功能检验', 'FUNCTION', 4),
('inspection_category', '防水结构检验', 'WATERPROOF', 5),
('inspection_category', '来料检验', 'INCOMING', 6);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('outsource_status', '草稿', 'DRAFT', 1),
('outsource_status', '待审批', 'PENDING', 2),
('outsource_status', '审批通过', 'APPROVED', 3),
('outsource_status', '审批驳回', 'REJECTED', 4),
('outsource_status', '已过期', 'EXPIRED', 5),
('outsource_status', '已作废', 'VOID', 6);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('mold_status', '设计中', 'IN_DESIGN', 1),
('mold_status', '加工中', 'IN_MACHINING', 2),
('mold_status', '试模中', 'TRIAL', 3),
('mold_status', '量产中', 'IN_PRODUCTION', 4),
('mold_status', '维修保养', 'MAINTENANCE', 5),
('mold_status', '报废', 'OBSOLETE', 6),
('mold_status', '封存', 'SEALED', 7);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('model3d_type', '外观3D', 'APPEARANCE', 1),
('model3d_type', '产品结构3D', 'STRUCTURE', 2),
('model3d_type', '模流报告', 'MOLD_FLOW', 3),
('model3d_type', '模具拆模3D', 'MOLD', 4),
('model3d_type', '爆炸图', 'EXPLODE', 5);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('archive_status', '新建草稿', 'DRAFT', 1),
('archive_status', '内部评审', 'REVIEWING', 2),
('archive_status', '正式发布', 'RELEASED', 3),
('archive_status', '变更迭代', 'CHANGING', 4),
('archive_status', '作废', 'OBSOLETE', 5),
('archive_status', '封存归档', 'SEALED', 6);

-- 编号序列补充
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length) VALUES
('OUTSOURCE_NO', 'OS',  'yyyyMMdd', 4),
('MOLD_NO',      'MO',  'yyyyMMdd', 4),
('INSPECT_NO',   'IS',  'yyyyMMdd', 4),
('MODEL3D_NO',   'M3',  'yyyyMMdd', 4)
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 权限菜单补充 ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order) VALUES
(108, 0, 'model3d',     '3D模型库',  1, '/model3d',   'model3d/index',   'View',      8),
(109, 0, 'archive',     '档案管理',  1, '/archive',   'archive/index',   'FolderOpened', 9),
(110, 0, 'outsourcing', '外协发图',  1, '/outsourcing','outsourcing/index','Promotion', 10)
ON CONFLICT (id) DO NOTHING;

-- 品质按钮
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1041, 104, 'quality:add',    '新增标准', 2, 1),
(1042, 104, 'quality:edit',   '编辑标准', 2, 2),
(1043, 104, 'quality:export', '导出基准书', 2, 3),
(1044, 104, 'quality:release','生效标准', 2, 4)
ON CONFLICT (id) DO NOTHING;

-- 外协按钮
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1101, 110, 'outsourcing:add',     '新建申请', 2, 1),
(1102, 110, 'outsourcing:approve', '审批',     2, 2),
(1103, 110, 'outsourcing:download','下载',     2, 3)
ON CONFLICT (id) DO NOTHING;

-- 模具按钮
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1031, 103, 'mold:add',      '新增台账', 2, 1),
(1032, 103, 'mold:edit',     '编辑台账', 2, 2),
(1033, 103, 'mold:trial',    '试模记录', 2, 3),
(1034, 103, 'mold:maintain', '保养维修', 2, 4)
ON CONFLICT (id) DO NOTHING;

-- 档案按钮
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1091, 109, 'archive:view',   '查看档案', 2, 1),
(1092, 109, 'archive:upload', '上传文件', 2, 2),
(1093, 109, 'archive:download','下载文件',2, 3)
ON CONFLICT (id) DO NOTHING;

-- 3D按钮
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order) VALUES
(1081, 108, 'model3d:upload',  '上传模型', 2, 1),
(1082, 108, 'model3d:explode', '3D爆炸',   2, 2),
(1083, 108, 'model3d:preview', '在线预览', 2, 3)
ON CONFLICT (id) DO NOTHING;

-- 给研发工程师补充新权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ENGINEER'
  AND p.perm_code IN (
    'quality','model3d','archive','outsourcing',
    'quality:add','quality:edit','quality:export','quality:release',
    'outsourcing:add','outsourcing:approve',
    'mold:add','mold:edit','mold:trial','mold:maintain',
    'archive:view','archive:upload','archive:download',
    'model3d:upload','model3d:explode','model3d:preview'
  )
ON CONFLICT DO NOTHING;
