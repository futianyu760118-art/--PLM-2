# AEOS 研发自治中心 V0.2 数据库迁移与回退 Runbook

## 1. 适用范围

本Runbook对应 `database/26_aeos_rd_v0_2.sql`。

新增能力包括：

- 关键节点双级审批
- 项目正式变更
- Gate Namespace / Rule
- AEOS Evidence Registry
- FileObject / ArchiveReference
- Metric Object治理字段
- R&D Action Center数据底座
- 新增权限能力

## 2. 上线前检查

1. 备份 PostgreSQL，记录备份文件、时间、操作者。
2. 记录当前应用 commit SHA。
3. 确认 `database/24_project_progress.sql` 和 `25_node_evidence_text.sql` 已执行。
4. 确认 `plm_project_node`、`plm_metric_def`、`sys_permission`、`sys_role_permission` 存在。
5. 在隔离环境先执行本迁移并跑后端测试。
6. G2未冻结的 Gate/Metric 必须保持 DRAFT/PENDING/PROPOSED，不得改为生产Active。

## 3. 执行

新数据库初始化时，按数字顺序执行数据库脚本。

现有数据库不会因为仓库增加SQL而自动升级；必须显式执行：

```bash
psql -v ON_ERROR_STOP=1 -f database/26_aeos_rd_v0_2.sql
```

执行失败必须停止，不允许忽略错误继续发布应用。

## 4. 迁移后验证

至少检查：

```sql
SELECT status, count(*) FROM plm_project_node GROUP BY status;
SELECT * FROM sys_permission
 WHERE perm_code IN (
   'project:node:submit',
   'project:node:approve:rd',
   'project:node:approve:gm',
   'rd:action:edit',
   'rd:action:accept',
   'rd:action:close'
 );

SELECT namespace_code, status, version_no
FROM aeos_gate_namespace
ORDER BY namespace_code;

SELECT metric_code, threshold_status, approval_status, version_no
FROM plm_metric_def
WHERE metric_code IN (
  'NODE_COMPLETION_RATE',
  'KEY_NODE_COMPLETION_RATE',
  'ON_TIME_RATE',
  'PROJECT_DATA_COMPLETENESS',
  'CHANGE_STABILITY_INDEX',
  'PROJECT_HEALTH_SCORE'
);
```

确认 `plm_project_node.status` 已扩展到 VARCHAR(24)，否则 `READY_FOR_APPROVAL` 无法写入。

## 5. 应用层验证

1. 普通节点可按原规则完成。
2. 关键节点直接写 DONE 被拒绝。
3. 关键节点具备实际日期+Evidence后可提交审批。
4. RD_LEAD审批后进入 `RD_APPROVED`。
5. GM批准后进入 `DONE`。
6. RD与GM同一用户审批被拒绝。
7. 新Evidence出现 `aeos_evidence_id`，并在 `aeos_evidence` 有登记。
8. 删除节点Evidence时 Registry 状态变为 `REVOKED`。
9. progress-check 返回 `formulaVersion=AEOS-RD-HS-V0.2`。

## 6. 回退原则

### 6.1 代码回退

优先通过 Git 回退本PR到上一个已验证版本。

### 6.2 数据库降级

如果新表已经产生真实审批、Evidence或Action记录，**禁止直接 DROP**。应：

1. 回退应用到旧版本；
2. 撤销新增权限映射；
3. 停止新表写入；
4. 保留新表作为审计数据；
5. 评审后再做受控数据迁移/清理。

只有在隔离环境、且确认新表无真实业务数据时，才允许物理删除新增对象。

### 6.3 权限紧急降级

可先撤销高风险审批权限而不删除数据：

```sql
DELETE FROM sys_role_permission rp
USING sys_permission p
WHERE rp.permission_id = p.id
  AND p.perm_code IN (
    'project:node:approve:rd',
    'project:node:approve:gm',
    'rd:action:accept',
    'rd:action:close'
  );
```

此动作必须记录操作者、时间、原因和恢复条件。

## 7. 发布门槛

- Maven tests通过。
- P0/P1 = 0。
- 备份与恢复路径已验证。
- 审批角色权限已由项目决策者确认。
- G2 Pending/Proposed项未被误配置成Active。
