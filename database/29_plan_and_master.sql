-- =============================================================================
-- HJ-PLM 增量 29: 研发自治中心 - 计划表(甘特) + 基础数据(主数据 M_*)
-- 计划表: 项目明细表 PLAN 节点打开填写 (对应模板 8甘特图)
-- 基础数据: 统一放系统管理模块 (对应模板 M_客户/M_产品/M_物料/M_供应商/M_人员/M_部门)
-- =============================================================================

-- 8甘特图 -> 计划表
CREATE TABLE IF NOT EXISTS plm_plan_sheet (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(64), stage VARCHAR(64), seq INT, work_item VARCHAR(256),
    month VARCHAR(16), plan_start DATE, plan_end DATE, owner VARCHAR(64),
    confirmer VARCHAR(128), form_name VARCHAR(128),
    status VARCHAR(32) DEFAULT '计划', remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_plan_sheet_proj ON plm_plan_sheet(project_no);

-- M_客户
CREATE TABLE IF NOT EXISTS m_customer (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64), name VARCHAR(256), type VARCHAR(32), level VARCHAR(16),
    contact VARCHAR(64), phone VARCHAR(64), region VARCHAR(64), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
-- M_产品
CREATE TABLE IF NOT EXISTS m_product (
    id BIGSERIAL PRIMARY KEY,
    model VARCHAR(128), name VARCHAR(256), spec VARCHAR(128), power VARCHAR(64),
    voltage VARCHAR(64), cct VARCHAR(64), luminous VARCHAR(64), ra VARCHAR(64),
    waterproof VARCHAR(64), dimension VARCHAR(128), material VARCHAR(64), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
-- M_物料
CREATE TABLE IF NOT EXISTS m_material (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64), name VARCHAR(256), spec VARCHAR(256), unit VARCHAR(32),
    category VARCHAR(64), supplier_code VARCHAR(64), price NUMERIC(18,4), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
-- M_供应商
CREATE TABLE IF NOT EXISTS m_supplier (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64), name VARCHAR(256), contact VARCHAR(64), phone VARCHAR(64),
    category VARCHAR(128), level VARCHAR(16), address VARCHAR(256), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
-- M_人员
CREATE TABLE IF NOT EXISTS m_person (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64), name VARCHAR(64), dept VARCHAR(64), position VARCHAR(64), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
-- M_部门
CREATE TABLE IF NOT EXISTS m_department (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64), name VARCHAR(64), remark TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 基础数据种子 (示例, 可改; 仅当空表时插入)
INSERT INTO m_department (code, name)
SELECT v.code, v.name FROM (VALUES
    ('D001','研发中心'),('D002','销售中心'),('D003','采购部'),('D004','品质部'),
    ('D005','生产部'),('D006','工程部'),('D007','财务部')
) AS v(code, name)
WHERE NOT EXISTS (SELECT 1 FROM m_department);

-- =============================================================================
-- Done.
-- =============================================================================
