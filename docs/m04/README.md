# M04 研发数据中台（PLM2 模块）

> 本目录是 M04 作为**独立业务模块**接入 PLM2 后的说明书。模块代码边界、数据库对象、接口、部署顺序、验收清单全在这里。

## 1. 模块定位

M04 解决三类研发基础数据的「有编码、有版本、有审批」问题：

| 子能力 | 解决的问题 | 关键对象 |
|---|---|---|
| 编码规则引擎 | 12 类单据编号写死在代码里，改格式要发版；项目号客户码分支无规则 | `plm_code_rule` / `plm_code_rule_segment` / `sys_sequence` |
| 文档中心 | 图纸散在共享盘，无版本链、无五区管控、无权限 | `plm_document` / `plm_document_version` / `plm_document_acl` |
| 审批引擎 | 每类单据各写一套审批，待办与流水账不一致 | `plm_approval_flow` / `_node` / `_instance` / `_task` |
| 变更申请 ECR | 变更只有 ECN 没有申请入口，变更必要性无人把关 | `plm_ecr` |

## 2. 代码边界

```
plm-backend/src/main/java/com/hjgd/plm/m04/
├── package-info.java          模块说明（包级 Javadoc）
├── codegen/                   编码规则引擎（5 类）
│   ├── controller/CodeGenController.java      取号 / 试算
│   ├── controller/CodeRuleController.java     规则 CRUD + 试算
│   └── service/CodeGenService(+Impl)          规则渲染器（6 段类型）
│       service/ProjectCodeGenerator.java      项目号（客户码三级结构）
├── document/                  文档中心（16 类，五区状态机 + 版本链 + ACL）
├── approval/                  审批引擎（20 类，含 ApprovalTimeoutJob 每 10 分钟扫超时）
└── ecr/                       变更申请（9 类）

plm-frontend/src/views/m04/
├── codegen/index.vue          编码规则（/m04/coderule，仅 ADMIN）
├── document/index.vue         文档中心（/document/list）
├── ecr/index.vue              ECR 申请（/ecr/list）
└── approval/{pending,flow}.vue 审批待办（/approval/pending）、审批流配置（/m04/flow，仅 ADMIN）
```

**依赖方向**：`m04.*` 可以依赖 `common` / `system` / `project` 等平台能力；**平台模块不得反向依赖 m04**（唯一例外是编码引擎被业务模块调用，见下）。

被外部调用方（这是设计意图，不是耦合泄漏）：
- `material` / `project` / `document` 调 `m04.codegen.CodeGenService` 取号；
- `ecn.EcnServiceImpl` 调 `m04.approval.ApprovalService` 走审批链。

## 3. 数据库脚本（**必须按编号顺序执行**，全部幂等）

| 脚本 | 内容 |
|---|---|
| `59_m04_core_gap.sql` | 核心四表：`plm_document*`、`plm_approval_*`、`plm_ecr`、`plm_requirement*`、`plm_technical_risk` |
| `60_code_rule_legacy_align.sql` | 存量编号与老 PLM 对齐 |
| `61_m04_project_code_rule.sql` | 项目号规则（7 段：LITERAL/YEAR/SEQ/DICT/SEQ/FIELD/OPT_SEQ） |
| `62_m04_project_code_fields.sql` | 项目号字段映射视图 |
| `63_m04_customer_code_branch.sql` | 客户码分支（1008 / 1008-1 / 1008-1-1） |
| `64_m04_codegen_rule_engine.sql` | **编码引擎接管**：12 条 ACTIVE 规则 + 36 段 + `v_codegen_rule_effective` + 自检 |
| `65_m04_document_center.sql` | 文档中心：DOC 序列 / 规则 / 7 个权限点 / 15 个字典 / 4 个索引 |
| `66_m04_approval_flow_seed.sql` | 审批流：`context_json` 列 + 3 条流程 5 个节点 + 5 个权限点 + 权限路径对齐 `/m04/*` |

执行顺序约束：`64` 必须在后端发布**之前**执行（`SequenceService.nextValue()` 依赖 `sys_sequence.last_period` 列）。

## 4. 接口一览

| 前缀 | 说明 | 主要端点 |
|---|---|---|
| `/v1/codegen` | 取号 | `POST /preview`、`POST /allocate` |
| `/v1/coderule` | 规则管理 | `GET /list`、`GET /{id}`、`PUT /{id}`、`PUT /{id}/status`、`DELETE /{id}`、`POST /preview` |
| `/document`、`/v1/documents` | 文档中心 | `GET /page`、`GET /stats`、`PUT /{id}/state`、`GET/POST /{id}/versions`、`POST /{id}/versions/{vid}/restore`、`PUT /{id}/checkout|checkin`、`GET/POST /{id}/acl` |
| `/approval` | 审批引擎 | `GET /pending`、`POST /{instanceId}/act`、`GET /progress`、`GET /{instanceId}/history`、`GET /flows`、`POST /flows`、`PUT /flows/{id}/status` |
| `/ecr` | 变更申请 | `GET /page`、`PUT /{id}/submit`、`PUT /{id}/approve`、`POST /{id}/to-ecn`、`PUT /{id}/close` |

## 5. 前端导航

模块在侧栏以独立阶段 **「M04 研发数据中台」** 出现（`layout/index.vue` 的 `STAGE_ORDER`）：

- 业务页：文档中心 `/document/list`、ECR 申请 `/ecr/list`、审批待办 `/approval/pending`
- 配置页（仅 ADMIN）：编码规则 `/m04/coderule`、审批流配置 `/m04/flow`
- 旧入口 `/admin/coderule`、`/admin/approval` 保留 301 式前端跳转到 `/m04/*`
- `config/modes.js` 的五种呈现模式均已挂上 M04 入口，切换模式不会丢模块

## 6. 上线步骤

```bash
# 1) 备份（铁律：任何数据操作前先备份）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backup-db.ps1

# 2) 按序执行 59 → 66（在 plm-postgres 容器内）
docker exec -i plm-postgres psql -U plm -d plm_v4 < database/59_m04_core_gap.sql
# ... 60 ~ 66 依次

# 3) 后端（禁止 --no-deps 之外的花样，见 AGENTS.md）
docker compose up -d --no-build --no-deps backend

# 4) 前端
docker compose up -d --no-build --no-deps frontend
```

## 7. 验收清单

- [ ] 12 条规则在 `/m04/coderule` 全部 ACTIVE，示例编号与 `sys_sequence.prefix` 一致（ECR/ECN/HJ/MO/IQ/INS/EX/OS/PR/WI/LX）
- [ ] 料号取号结果与改造前**逐字节一致**（`HJ202611020001` 形态）
- [ ] 项目号按 `HJ.26.001.V1.1008-1` 形态生成，客户码三级分支正确
- [ ] 文档状态机拒绝非法流转（发布区不能直接作废）
- [ ] 提交 ECR 后 `/approval/pending` 出现待办，一审驳回整单终止、二审驳回退回一审
- [ ] ECR 通过后可一键转 ECN，ECN 单号接续取号不跳号
- [ ] `ApprovalTimeoutJob` 每 10 分钟扫描，超时 REMIND 生效（`plm.job.approval.timeout` 可设 `-` 停用）

## 8. 已知待办

1. **61 号脚本缺分隔符段**：项目号分段没有 `LITERAL '.'`，渲染器纯字符串拼接 → 实际输出 `HJ26001V11008` 而非 `HJ.26.001.V1.1008-1`，需补 4 个 `LITERAL '.'` 段。
2. 料号是否按类码体系（`61PFD0401100-000`）重建，待业务确认。
3. 老 PLM 数据迁移已冻结（「没意义不迁、重复不迁」），文件实体 25.4GB 仍在 43 服务器磁盘。
