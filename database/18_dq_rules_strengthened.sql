-- =============================================================================
-- HJ-PLM V1.1 增量 18: 强化 DQ 自检规则
-- 说明: 替换 13_dq_rules_webhook.sql 中的占位规则(`SELECT 0` / `true`)
--       为真实业务校验(BOM/ECN/发布门禁/版本/WIP 等)。
-- 所有 UPDATE 幂等,可重复执行。
-- =============================================================================

-- 1. BOM_QTY_POSITIVE: 检查 release 的 BOM 是否有用量<=0 的明细 (BLOCK)
-- 原为静态占位 'SELECT 0', 升级为查 RELEASED BOM 的坏行
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom_item i JOIN plm_bom b ON i.bom_id=b.id WHERE b.status=''RELEASED'' AND i.quantity <= 0 AND i.deleted=0',
    message_template = '已发布BOM存在用量<=0的明细行'
WHERE rule_code = 'BOM_QTY_POSITIVE';

-- 2. BOM_NO_CYCLE: 检测 BOM 循环引用 (BLOCK) - 使用 ltree nlevel 检测异常层级
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom_item WHERE nlevel(path) > 12',
    message_template = 'BOM存在超过12层嵌套或循环引用'
WHERE rule_code = 'BOM_NO_CYCLE';

-- 3. BOM_CHILD_EXISTS: 子件料号必须存在于 plm_material (BLOCK)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom_item i LEFT JOIN plm_material m ON i.part_no=m.part_no WHERE m.id IS NULL AND i.deleted=0',
    message_template = 'BOM存在无效子件料号(主数据缺失)'
WHERE rule_code = 'BOM_CHILD_EXISTS';

-- 4. BOM_HAS_ITEMS: RELEASED BOM 必须有非根明细 (BLOCK)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom b WHERE b.status=''RELEASED'' AND b.deleted=0 AND (SELECT COUNT(1) FROM plm_bom_item i WHERE i.bom_id=b.id AND i.parent_item_id>0 AND i.deleted=0) = 0',
    message_template = '已发布BOM无明细行'
WHERE rule_code = 'BOM_HAS_ITEMS';

-- 5. BOM_ROOT_MATCH: BOM 根料号必须存在于 plm_material (WARN)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom b LEFT JOIN plm_material m ON b.root_part_no=m.part_no WHERE m.id IS NULL AND b.deleted=0',
    message_template = 'BOM根料号在主数据中不存在'
WHERE rule_code = 'BOM_ROOT_MATCH';

-- 6. BOM_ITEM_UNIT: 子件应有单位 (INFO)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom_item WHERE (unit IS NULL OR unit='''') AND parent_item_id>0 AND deleted=0',
    message_template = 'BOM子件单位缺失'
WHERE rule_code = 'BOM_ITEM_UNIT';

-- 7. BOM_KEY_PART_PRICE: 关键部件(自制定义为 make_type=0)应有 standard_cost (WARN)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_bom_item i JOIN plm_material m ON i.part_no=m.part_no WHERE m.make_type=0 AND (m.standard_cost IS NULL OR m.standard_cost=0) AND i.deleted=0',
    message_template = '关键自制件标准成本未维护'
WHERE rule_code = 'BOM_KEY_PART_PRICE';

-- 8. ECN_IMPACT_REQUIRED: 生效前必须有影响面记录 (BLOCK) - 升级为查实际 impact 表
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_ecn e WHERE e.status IN (''APPROVED'',''EFFECTIVE'') AND (SELECT COUNT(1) FROM plm_ecn_impact i WHERE i.ecn_id=e.id) = 0',
    message_template = 'ECN影响面缺失,不可生效'
WHERE rule_code = 'ECN_IMPACT_REQUIRED';

-- 9. ECN_REASON_LENGTH: 变更原因>=20字 (WARN) - 升级为查实际原因长度
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_ecn WHERE LENGTH(COALESCE(change_reason,'''')) < 20',
    message_template = '变更原因描述过短(建议20字以上)'
WHERE rule_code = 'ECN_REASON_LENGTH';

-- 10. ECN_VERSION_FORMAT: 版本号格式 Vx.y (WARN)
UPDATE plm_dq_rule SET
    expression = 'SELECT COUNT(*) FROM plm_ecn WHERE version_after IS NOT NULL AND version_after !~ ''^V[0-9]+(\\.[0-9]+)*$''',
    message_template = '变更后版本号格式不规范(建议 V1.0 / V2.1)'
WHERE rule_code = 'ECN_VERSION_FORMAT';

-- 11. WIP_DRAFT_LIMIT: 保持原样 (硬编码 DRAFT 类型, 全局过)
--     注: 单人 WIP 限制需 OwnerId 维度, plm_material 无此字段, 仅以状态判断
UPDATE plm_dq_rule SET
    expression = 'true',
    message_template = '个人草稿料号过多,请先清理(WIP治理)'
WHERE rule_code = 'WIP_DRAFT_LIMIT';

-- 12. RELEASE_HAS_DQ_PASS: 保持原样 (发布门禁由 MaterialService.assertNoBlock 强制, 此规则仅做提示)
UPDATE plm_dq_rule SET
    expression = 'true',
    message_template = '存在阻断级质量问题,禁止发布(系统自动校验)'
WHERE rule_code = 'RELEASE_HAS_DQ_PASS';

-- 13. 新增: PART_UNIT_PRICE (WARN) - 成品应维护建议零售价
INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, enabled) VALUES
('PART_FG_PRICE', '成品价格', 'PART', 'WARN', 'FIELD', '#materialType == "FINISHED" && #standardCost != null', '成品建议维护标准成本(报价基础)', true)
ON CONFLICT (rule_code) DO NOTHING;

-- 14. 新增: BOM_OBSOLETE_VERSION (WARN) - 同一料号有多个 RELEASED 版本(版本治理)
INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, enabled) VALUES
('BOM_MULTI_RELEASED', '多版本并存', 'BOM', 'WARN', 'SQL',
 'SELECT COUNT(*) FROM (SELECT root_part_no, COUNT(1) AS cnt FROM plm_bom WHERE status=''RELEASED'' AND deleted=0 GROUP BY root_part_no) t WHERE t.cnt > 1',
 '同一料号存在多个 RELEASED 版本(版本治理预警)', true)
ON CONFLICT (rule_code) DO NOTHING;

-- 15. 新增: ECN_LONG_PENDING (WARN) - ECN PENDING_L1/L2 超过 14 天
INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, enabled) VALUES
('ECN_LONG_PENDING', '审批超时', 'ECN', 'WARN', 'SQL',
 'SELECT COUNT(*) FROM plm_ecn WHERE status IN (''PENDING_L1'',''PENDING_L2'') AND apply_time < NOW() - INTERVAL ''14 days''',
 'ECN 审批超过 14 天仍未关闭(流转预警)', true)
ON CONFLICT (rule_code) DO NOTHING;

-- 16. 新增: ARCHIVE_ORPHAN (WARN) - 档案无文件(空目录)
INSERT INTO plm_dq_rule (rule_code, name, object_type, severity, check_type, expression, message_template, enabled) VALUES
('ARCHIVE_NO_FILE', '空档案', 'ARCHIVE', 'WARN', 'SQL',
 'SELECT COUNT(*) FROM plm_archive_node n LEFT JOIN plm_archive_node_file f ON n.id=f.node_id WHERE n.deleted=0 AND f.id IS NULL',
 '档案节点无附件(空目录预警)', true)
ON CONFLICT (rule_code) DO NOTHING;