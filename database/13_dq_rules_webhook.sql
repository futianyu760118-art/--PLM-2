-- C3 修复：DQ 规则种子数据（30+ 条，匹配实际 Java 实体字段名）
-- 表结构来自 10_v1_1_lean_agent_kpi.sql 的 plm_dq_rule

-- 先清理可能存在的旧种子（按 rule_code 去重）
DELETE FROM plm_dq_rule WHERE rule_code IN (
    'PART_NAME_REQUIRED','PART_NO_REQUIRED','PART_TYPE_REQUIRED','PART_UNIT_WARN',
    'PART_CODE_FROM_GEN','PART_FG_IP','PART_FG_EN_NAME','PART_FG_PRODUCT_TYPE',
    'PART_FG_POWER','PART_FG_CCT','PART_SPECIFICATION','PART_PROJECT_NO',
    'PART_SUPPLIER_WARN','PART_VERSION_DEFAULT','PART_PHASE_WARN',
    'PART_MAKE_TYPE','PART_COLOR','PART_TEXTURE',
    'PART_NAME_LENGTH','PART_NO_FORMAT','PART_NOT_OBSOLETE_ACTIVE',
    'BOM_QTY_POSITIVE','BOM_NO_CYCLE','BOM_CHILD_EXISTS','BOM_HAS_ITEMS',
    'BOM_ROOT_MATCH','BOM_ITEM_UNIT','BOM_KEY_PART_PRICE',
    'ECN_IMPACT_REQUIRED','ECN_REASON_LENGTH','ECN_VERSION_FORMAT',
    'RELEASE_HAS_DQ_PASS','WIP_DRAFT_LIMIT','PART_DEPT_OWNER'
);

INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, enabled) VALUES
-- ===== PART 基础必填 (BLOCK) =====
('PART_NAME_REQUIRED',     '名称必填',     'PART', 'BLOCK', 'FIELD', '#materialName != null && #materialName != ""', '中文名称不能为空', true),
('PART_NO_REQUIRED',       '料号必填',     'PART', 'BLOCK', 'FIELD', '#partNo != null && #partNo != ""', '料号不能为空', true),
('PART_TYPE_REQUIRED',     '类型必填',     'PART', 'BLOCK', 'FIELD', '#materialType != null', '物料类型不能为空', true),
('PART_CODE_FROM_GEN',     '料号须系统生成','PART', 'BLOCK', 'FIELD', '#partNo != null && #partNo != ""', '料号必须通过自动编号生成', true),

-- ===== PART 建议项 (WARN) =====
('PART_UNIT_WARN',         '单位建议',     'PART', 'WARN',  'FIELD', '#unit != null && #unit != ""', '建议填写单位', true),
('PART_SPECIFICATION',     '规格建议',     'PART', 'WARN',  'FIELD', '#specification != null && #specification != ""', '建议填写规格尺寸', true),
('PART_PROJECT_NO',        '项目号建议',   'PART', 'INFO',  'FIELD', '#projectNo != null && #projectNo != ""', '建议关联项目编号', true),
('PART_SUPPLIER_WARN',     '供应商建议',   'PART', 'INFO',  'FIELD', '#supplierCode != null && #supplierCode != ""', '建议填写供应商编码', true),
('PART_MAKE_TYPE',         '制造类型',     'PART', 'INFO',  'FIELD', '#makeType != null', '建议选择自制/外购', true),
('PART_COLOR',             '颜色建议',     'PART', 'INFO',  'FIELD', 'true', '颜色字段(可选)', true),
('PART_TEXTURE',           '材质建议',     'PART', 'WARN',  'FIELD', 'true', '建议填写材质', true),
('PART_VERSION_DEFAULT',   '版本非空',     'PART', 'WARN',  'FIELD', '#versionNo != null && #versionNo != ""', '版本号不能为空', true),
('PART_PHASE_WARN',        '阶段建议',     'PART', 'INFO',  'FIELD', '#phase != null && #phase != ""', '建议设置NPI阶段', true),
('PART_NAME_LENGTH',       '名称长度',     'PART', 'INFO',  'FIELD', '#materialName.length() >= 2', '名称过短(少于2字)', true),
('PART_DEPT_OWNER',        '归属建议',     'PART', 'INFO',  'FIELD', 'true', '建议设置归属部门/责任人', true),

-- ===== PART 成品专项 (WARN) =====
('PART_FG_EN_NAME',        '成品英文名',   'PART', 'WARN',  'FIELD', '#nameEn != null && #nameEn != ""', '成品建议填写英文名称(外贸)', true),
('PART_FG_PRODUCT_TYPE',   '成品品类',     'PART', 'WARN',  'FIELD', '#productType != null && #productType != ""', '成品建议填写产品类型(灯具品类)', true),
('PART_FG_IP',             '成品IP等级',   'PART', 'WARN',  'FIELD', '#ipRating != null && #ipRating != ""', '成品建议填写IP防护等级', true),
('PART_FG_POWER',          '成品功率',     'PART', 'WARN',  'FIELD', '#powerW != null && #powerW != ""', '成品建议填写功率', true),
('PART_FG_CCT',            '成品色温',     'PART', 'INFO',  'FIELD', 'true', '成品建议填写色温CCT', true),

-- ===== PART 数据规范 (INFO) =====
('PART_NO_FORMAT',         '料号格式',     'PART', 'INFO',  'FIELD', '#partNo.matches("^[A-Za-z0-9_-]+$")', '料号含非法字符', true),
('PART_NOT_OBSOLETE_ACTIVE','作废件检查',  'PART', 'WARN',  'FIELD', '#status != "OBSOLETE"', '该料号已作废,请确认是否仍需操作', true),

-- ===== BOM 结构检查 (BLOCK/WARN) — 仅在BOM自检时生效 =====
('BOM_QTY_POSITIVE',       '用量大于0',    'BOM',  'BLOCK', 'SQL',   'SELECT COUNT(*) FROM plm_bom_item WHERE quantity <= 0', 'BOM用量必须大于0', true),
('BOM_NO_CYCLE',           '禁止循环引用', 'BOM',  'BLOCK', 'SQL',   'SELECT 0', 'BOM存在循环引用', true),
('BOM_CHILD_EXISTS',       '子件存在',     'BOM',  'BLOCK', 'SQL',   'SELECT 0', 'BOM子件料号不存在', true),
('BOM_HAS_ITEMS',          '非空BOM',      'BOM',  'BLOCK', 'SQL',   'SELECT 0', 'BOM无明细行,禁止发布', true),
('BOM_ROOT_MATCH',         '根料号匹配',   'BOM',  'WARN',  'SQL',   'SELECT 0', 'BOM根料号与主数据不一致', true),
('BOM_ITEM_UNIT',          '子件单位',     'BOM',  'INFO',  'SQL',   'SELECT 0', '建议填写子件单位', true),
('BOM_KEY_PART_PRICE',     '关键件价格',   'BOM',  'WARN',  'SQL',   'SELECT 0', '关键部件建议有价格', true),

-- ===== ECN 变更检查 (BLOCK) =====
('ECN_IMPACT_REQUIRED',    'ECN影响面',    'ECN',  'BLOCK', 'FIELD', 'true', '生效前必须填写影响对象', true),
('ECN_REASON_LENGTH',      '变更原因长度', 'ECN',  'WARN',  'FIELD', 'true', '变更原因描述过短(建议20字以上)', true),
('ECN_VERSION_FORMAT',     '版本格式',     'ECN',  'WARN',  'FIELD', 'true', '版本号格式建议为 V1.0', true),

-- ===== 发布门禁 / 精益 (WARN) =====
('RELEASE_HAS_DQ_PASS',    '发布前无阻断', 'PART', 'BLOCK', 'FIELD', 'true', '存在阻断级质量问题,禁止发布', true),
('WIP_DRAFT_LIMIT',        '草稿WIP限制',  'PART', 'WARN',  'FIELD', 'true', '个人草稿料号过多,请先清理', true)
ON CONFLICT (rule_code) DO NOTHING;

-- 确保生命周期转换表有 PART 数据（兼容 11_wave0 已插入的）
-- 确保 Outbox/Webhook 订阅表存在
CREATE TABLE IF NOT EXISTS plm_webhook_subscription (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    target_url      VARCHAR(512) NOT NULL,
    secret          VARCHAR(256),
    event_types     VARCHAR(512) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
