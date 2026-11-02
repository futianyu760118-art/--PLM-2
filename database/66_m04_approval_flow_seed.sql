-- =============================================================================
-- 66_m04_approval_flow_seed.sql
-- 审批流引擎落地：实例上下文列 + 三条默认流程（ECN / ECR / 文档发布）+ 权限点
--
-- 幂等：全部 IF NOT EXISTS / ON CONFLICT DO NOTHING / NOT EXISTS 包裹
-- 前置：59_m04_core_gap.sql（四张 plm_approval_* 表 + plm_ecr）
-- 说明：本脚本不修改任何业务表数据，只补列、补配置、补权限
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. 实例保存发起上下文
--    引擎推进到下一节点时要用它重新评估 condition_expr，否则重启后条件节点判断会丢
-- -----------------------------------------------------------------------------
ALTER TABLE plm_approval_instance ADD COLUMN IF NOT EXISTS context_json TEXT;
COMMENT ON COLUMN plm_approval_instance.context_json IS '发起时的业务上下文(JSON)，用于后续节点条件判断与审计';

-- -----------------------------------------------------------------------------
-- 2. 待办查询索引：待办箱按 approver + action IS NULL 过滤
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_apprtask_pending
    ON plm_approval_task(approver) WHERE action IS NULL;

-- -----------------------------------------------------------------------------
-- 3. 默认流程定义
--    审批人全部用角色表达，改人不改流程；角色下无人时引擎回落 ADMIN
--    节点 on_reject 默认 BACK：二审驳回退回到一审，符合「第 2 级驳回退到第 1 级」
-- -----------------------------------------------------------------------------
INSERT INTO plm_approval_flow (flow_code, object_type, name, version_no, status, remark)
VALUES
    ('ECN_DEFAULT', 'ECN',        'ECN 变更审批（默认两级）', 'V1', 'ENABLED',
     '一审研发、二审管理；驳回退回到上一节点'),
    ('ECR_DEFAULT', 'ECR',        'ECR 变更申请审批（默认两级）', 'V1', 'ENABLED',
     'ECR 通过后由申请人一键转 ECN'),
    ('DOC_RELEASE', 'DOCUMENT',   '文档发布审批（默认一级）', 'V1', 'ENABLED',
     '图纸发布前审核；当前文档模块走自有状态机，此流程供后续接入')
ON CONFLICT (flow_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. 节点定义
--    注意：DEPT_LEADER 因 sys_department 无主管列暂降级按角色解析，这里统一用 ROLE
-- -----------------------------------------------------------------------------
INSERT INTO plm_approval_node
    (flow_id, seq, node_code, node_name, approver_type, approver_value,
     condition_expr, parallel_group, timeout_hours, on_reject, on_timeout, remark)
SELECT f.id, v.seq, v.node_code, v.node_name, v.approver_type, v.approver_value,
       v.condition_expr, v.parallel_group, v.timeout_hours, v.on_reject, v.on_timeout, v.remark
FROM plm_approval_flow f
JOIN (VALUES
    -- ECN：一审研发工程师，二审系统管理员（=管理层）
    ('ECN_DEFAULT', 1, 'ECN_L1', '研发一审', 'ROLE', 'ENGINEER', NULL::varchar, NULL::varchar, 24, 'TERMINATE', 'REMIND', '结构/工艺方案是否可行'),
    ('ECN_DEFAULT', 2, 'ECN_L2', '终审',     'ROLE', 'ADMIN',    NULL::varchar, NULL::varchar, 48, 'BACK',      'REMIND', '成本与交付影响'),
    -- ECR：一级研发确认，二级终审
    ('ECR_DEFAULT', 1, 'ECR_L1', '研发确认', 'ROLE', 'ENGINEER', NULL::varchar, NULL::varchar, 24, 'TERMINATE', 'REMIND', '变更必要性'),
    ('ECR_DEFAULT', 2, 'ECR_L2', '变更终审', 'ROLE', 'ADMIN',    NULL::varchar, NULL::varchar, 48, 'BACK',      'REMIND', '通过后转 ECN'),
    -- 文档发布：一级
    ('DOC_RELEASE', 1, 'DOC_L1', '图纸审核', 'ROLE', 'ENGINEER', NULL::varchar, NULL::varchar, 24, 'TERMINATE', 'REMIND', '发布前审核')
) AS v(flow_code, seq, node_code, node_name, approver_type, approver_value,
       condition_expr, parallel_group, timeout_hours, on_reject, on_timeout, remark)
  ON f.flow_code = v.flow_code
WHERE NOT EXISTS (
    SELECT 1 FROM plm_approval_node n WHERE n.flow_id = f.id AND n.seq = v.seq
);

-- -----------------------------------------------------------------------------
-- 5. 权限点
-- -----------------------------------------------------------------------------
INSERT INTO sys_permission (perm_code, perm_name, perm_type, path, component, sort_order)
SELECT v.perm_code, v.perm_name, v.perm_type, v.path, v.component, v.sort_order
FROM (VALUES
    ('ecr:list',     'ECR 变更申请-查看', 1, '/ecr/list',       'm04/ecr/index',        70),
    ('ecr:add',      'ECR 变更申请-新建', 2, NULL::varchar,     NULL::varchar,          71),
    ('ecr:edit',     'ECR 变更申请-编辑', 2, NULL::varchar,     NULL::varchar,          72),
    ('approval:list','审批流配置-查看',   1, '/m04/flow',      'm04/approval/flow',    73),
    ('approval:edit','审批流配置-编辑',   2, NULL::varchar,     NULL::varchar,          74)
) AS v(perm_code, perm_name, perm_type, path, component, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_code = v.perm_code);

-- M04 模块化改造：配置页从 /admin/* 迁到 /m04/*，已存在的权限行同步路径（可重复执行）
UPDATE sys_permission SET path = '/m04/flow', component = 'm04/approval/flow'
WHERE perm_code = 'approval:list'
  AND (path IS DISTINCT FROM '/m04/flow' OR component IS DISTINCT FROM 'm04/approval/flow');
UPDATE sys_permission SET component = 'm04/ecr/index'
WHERE perm_code = 'ecr:list' AND component IS DISTINCT FROM 'm04/ecr/index';

-- ADMIN 授权（其余角色按需在权限页面勾选）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.perm_code IN ('ecr:list', 'ecr:add', 'ecr:edit', 'approval:list', 'approval:edit')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- -----------------------------------------------------------------------------
-- 6. 自检：三条流程各应有两个/一个节点
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_flows INT;
    v_nodes INT;
    v_bad   INT;
BEGIN
    SELECT COUNT(*) INTO v_flows
    FROM plm_approval_flow
    WHERE flow_code IN ('ECN_DEFAULT', 'ECR_DEFAULT', 'DOC_RELEASE');

    SELECT COUNT(*) INTO v_nodes
    FROM plm_approval_node n
    JOIN plm_approval_flow f ON f.id = n.flow_id
    WHERE f.flow_code IN ('ECN_DEFAULT', 'ECR_DEFAULT', 'DOC_RELEASE');

    SELECT COUNT(*) INTO v_bad
    FROM plm_approval_flow f
    WHERE f.flow_code IN ('ECN_DEFAULT', 'ECR_DEFAULT', 'DOC_RELEASE')
      AND NOT EXISTS (SELECT 1 FROM plm_approval_node n WHERE n.flow_id = f.id);

    IF v_flows <> 3 THEN
        RAISE EXCEPTION '流程定义数量异常：期望 3，实际 %', v_flows;
    END IF;
    IF v_nodes <> 5 THEN
        RAISE EXCEPTION '审批节点数量异常：期望 5，实际 %', v_nodes;
    END IF;
    IF v_bad > 0 THEN
        RAISE EXCEPTION '存在没有节点的流程：% 条', v_bad;
    END IF;
    RAISE NOTICE '审批流种子 OK：% 条流程 / % 个节点', v_flows, v_nodes;
END $$;

COMMIT;

-- =============================================================================
-- 回滚（如需）
-- =============================================================================
-- DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE perm_code LIKE 'ecr:%' OR perm_code LIKE 'approval:%');
-- DELETE FROM sys_permission WHERE perm_code LIKE 'ecr:%' OR perm_code LIKE 'approval:%';
-- DELETE FROM plm_approval_node WHERE flow_id IN (SELECT id FROM plm_approval_flow WHERE flow_code IN ('ECN_DEFAULT','ECR_DEFAULT','DOC_RELEASE'));
-- DELETE FROM plm_approval_flow WHERE flow_code IN ('ECN_DEFAULT','ECR_DEFAULT','DOC_RELEASE');
-- ALTER TABLE plm_approval_instance DROP COLUMN IF EXISTS context_json;
-- =============================================================================
