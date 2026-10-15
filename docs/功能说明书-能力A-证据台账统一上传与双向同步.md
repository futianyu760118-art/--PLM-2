# 功能说明书（能力 A · 证据台账统一上传与双向同步）

适用版本：恒剑光电 PLM V4.0（能力线 A，PAND-200 / 阶段 3）
日期：2026-10-14

---

## 1. 范围

本说明书覆盖能力线 A 的六个功能点，全部纳入本轮实现：

| 编号 | 功能点 | 交付内容 | 主要验收标准 |
|---|---|---|---|
| A1 | 证据台账统一上传 | 单一界面完成「填信息 → 传附件 → 关联对象 → 提交」，全过程零页面跳转 | AC-A1-1 ~ A1-8 |
| A2 | 台账信息字段与录入规则 | 完整字段表、系统生成字段只读、必填与长度/时间边界、编号全局唯一、类型可配置、草稿豁免 | AC-A2-1 ~ A2-7 |
| A3 | 台账 ↔ 需求模块/表单 双向同步 | 引用关联 + 实时双向一致 + 变更通知；不产生副本 | AC-A3-1 ~ A3-8 |
| A4 | 关联管理与变更通知 | 一条证据关联 1~N 个对象、关联清单、解除关联、失效标注；变更通知与订阅开关 | AC-A4-1 ~ A4-11 |
| A5 | 检索、筛选与批量操作 | 组合筛选（与语义）、分页、批量上传、批量关联、成功/失败反馈 | AC-A5-1 ~ A5-9 |
| A6 | 权限与审计 | 界面按钮级权限、URL 越权拦截、越权留痕、逐条操作日志（含前后值/IP）、只读日志、≥3 年保留、按证据编号全轨迹 | AC-A6-1 ~ A6-7 |

不在本轮范围：B 线（分类订阅报告）；模块侧的可编辑字段回写依赖 A3/A4 已实现的 `/ledger/sync`。

---

## 2. 核心设计：单一事实源 + 引用视图（零副本）

- `plm_ledger` 是证据的**唯一事实源**；「证据台账录入」与「资料汇总处」是**同一行数据的两个视图**（同一套列表/详情接口，仅默认筛选与列宽不同），不存在第二份数据，因此 AC-A3-3「两侧逐字段一致」由数据模型结构性保证，无需同步任务。
- 证据与业务对象之间是**多态引用**（`plm_ledger_link`：`ledger_id` + `object_type` + `object_id`），模块/表单侧的证据区通过 `GET /ledger/by-object` **实时读取**同一行 `plm_ledger`，不落地任何副本。
- 因此 AC-A3-1 / A3-5 / A3-6 / A4-6 的「≤ 1 分钟可见」不是靠定时任务争取的时延，而是**读取即最新**；1 分钟是保守上界。
- 附件只引用 `plm_file`（`plm_ledger_attachment.file_id`），台账不复制文件字节。

---

## 3. 数据模型

### 3.1 plm_ledger（证据主表，= 资料汇总处的数据来源）

| 字段 | 类型 | 说明 | 必填 | 来源 |
|---|---|---|---|---|
| `ledger_no` | VARCHAR(32) | 证据编号，`EV-yyyyMMdd-NNNN`，全局唯一 | 系统生成 | `sys_sequence(LEDGER_NO)`，前缀 `EV-`、周期 `yyyyMMdd`、4 位、分隔符 `-` |
| `name` | VARCHAR(100) | 证据名称 | 提交时必填 | 录入 |
| `type` | VARCHAR(32) | 证据类型（字典 `ledger_type`） | 提交时必填 | 录入 |
| `owner_id` | BIGINT | 责任人 | 提交时必填 | 录入（人员选择器） |
| `occur_date` | DATE | 发生时间 | 提交时必填 | 录入 |
| `version` | VARCHAR(20) | 版本 | 否 | 录入 |
| `dept_id` | BIGINT | 来源部门 | 提交时必填 | 录入（部门树选择器） |
| `status` | VARCHAR(16) | 草稿 DRAFT / 已提交 SUBMITTED / 已归档 ARCHIVED / 作废 VOID | 系统生成 | 录入时选择草稿或提交 |
| `remark` | VARCHAR(500) | 备注 | 否 | 录入 |
| `uploaded_by` / `uploaded_at` | BIGINT / TIMESTAMP | 上传人 / 上传时间 | 系统生成 | 服务端 |
| `last_modified_by` / `last_modified_at` | VARCHAR / TIMESTAMP | 最后修改人 / 时间 | 系统生成 | 任一写入方（台账侧或模块侧） |
| `revision` | INT | 乐观并发版本号，每次写入 +1 | 系统生成 | 服务端 |

草稿（DRAFT）时 `name/type/owner_id/occur_date/dept_id` 允许为空，完整必填校验推迟到「已提交」。

### 3.2 plm_ledger_attachment（附件引用）

`ledger_id`、`file_id`（引用 `plm_file`）、`file_name`、`file_ext`、`file_size`、`sort_order`、`uploaded_by`、`uploaded_at`。
唯一索引 `(ledger_id, file_id) WHERE deleted = 0`，同一文件不会被重复挂到同一条证据。

### 3.3 plm_ledger_link（多态关联引用）

`ledger_id`、`object_type`、`object_id`、`object_no`、`object_name`（后两列为展示冗余，用于对象被删除后仍可读），唯一索引 `(ledger_id, object_type, object_id) WHERE deleted = 0`。

### 3.4 plm_notification / plm_notification_setting

- `plm_notification`：`user_id`、`notify_type`、`title`、`content_json`（`{…extra, operator, time, changes:[{field,before,after,operator,time}]}`）、`object_type`、`object_id`、`read_status`、`agg_key`。
- `plm_notification_setting`：`user_id` + `notify_type` 维度的订阅开关。

### 3.5 审计

- `plm_entity_history`：`object_type='LEDGER'`、`object_id=<证据编号>`，按 `version` 记录快照与变更。
- `sys_operation_log`：显式写入，`params` 列内保存 `before`/`after` 前后值，另记操作人、IP、结果。
- 无痕删除：`deleted` 软删列（沿用全局约定）。

---

## 4. 功能点 × AC 对照

### A1 证据台账统一上传

| AC | 要求 | 实现与验证 |
|---|---|---|
| A1-1 | 台账页提供「新建证据」入口，在当前页面内打开录入表单，不发生页面级跳转 | `plm-frontend/src/views/ledger/index.vue` 的 `el-dialog`；模块侧入口 `LedgerEvidencePanel.goCreate()` 跳 `/ledger/list?objectType&objectId` 并在同一页内自动开窗 |
| A1-2 | 填信息 → 传附件 → 关联对象 → 提交，页面跳转次数 = 0 | 三项在同一弹窗的三个分区内完成；提交调用 `/ledger/batch-upload`（有附件）或 `POST /ledger`（草稿/无附件） |
| A1-3 | 提交成功后记录在列表可见且状态为「已提交」 | `LedgerServiceImpl.create/batchUpload` 落库 `status=SUBMITTED`，前端 `load()` 刷新列表 |
| A1-4 | 单条证据附件 ≤ 20 个 | `LedgerProperties.maxAttachments=20`；服务层 `validateFileIds` 与前端 `acceptFiles` 双向拦截 |
| A1-5 | 单文件 ≤ 200MB，等于上限可通过 | `maxFileSizeMb=200`，判据为 `fileSize > maxBytes`（等于通过） |
| A1-6 | 扩展名白名单：pdf/doc/docx/xls/xlsx/ppt/pptx/jpg/jpeg/png/zip/rar/dwg/dxf | `LedgerProperties.allowExts`；不在白名单报「不支持的附件格式: …」 |
| A1-7 | 提交时至少 1 个附件 | `validateFileIds`：非草稿且 0 附件 → 「至少上传 1 个附件」 |
| A1-8 | 中途中断不留半成品 | 附件先入 `plm_file` 暂存，台账记录在提交事务内一次落库；未提交不产生 `plm_ledger` 行 |

### A2 字段与录入规则

| AC | 要求 | 实现与验证 |
|---|---|---|
| A2-1 | 表单含全部字段；编号/上传时间/状态系统生成且只读 | 编号与上传时间在表单中为 disabled；状态由「提交/存为草稿」决定 |
| A2-2 | 名称、类型、关联对象、责任人、发生时间、附件、来源部门任一为空即拦截并逐项提示 | 前端 `validateForm` 汇总缺失项一次性提示「请补全必填项：…」；服务层 `validateEntityFields` 二次校验 |
| A2-3 | 名称 100/101、备注 500/501、版本 20/21 边界 | `nameMax/remarkMax/versionMax`；前端 `maxlength` + 服务层长度校验（等于可通过，超出报错） |
| A2-4 | 发生时间 = 当日可通过；> 当日拦截并提示「发生时间不得晚于当前日期」 | 前端日期选择器 `disabled-date` + `validateForm`；服务层同文案校验 |
| A2-5 | 编号全局唯一（并发 100 条不重复） | `sys_sequence` 原子取号 + `ledger_no` UNIQUE 约束 |
| A2-6 | 类型为可配置枚举，管理员增/停用后表单下拉同步 | 类型取自字典 `ledger_type`，`GET /ledger/options` 实时下发；历史数据不受影响 |
| A2-7 | 草稿不做必填校验，仅长度/格式校验；转提交时执行完整校验 | `validateEntityFields(ledger, draft)`；草稿仍校验类型合法性、责任人/部门存在性、长度与发生时间 |

### A3 双向同步

| AC | 要求 | 实现与验证 |
|---|---|---|
| A3-1 | 台账提交后，关联对象所在模块证据区 ≤ 1 分钟出现引用条目 | 模块侧 `GET /ledger/by-object` 实时读同一行（`LedgerRefVO`：编号/名称/类型/责任人/时间/附件数/状态） |
| A3-2 | 模块侧修改可编辑字段（类型、责任人、备注、版本）保存后，台账 ≤ 1 分钟同步 | `POST /ledger/sync`，字段白名单 `WRITABLE_FIELDS = {name,type,ownerId,occurDate,version,deptId,status,remark}` |
| A3-3 | 两侧逐字段一致 | 单一事实源 + 引用视图，结构性保证 |
| A3-4 | 台账「最后修改人/时间」随模块侧修改更新为模块侧操作人与时间 | `doApply` 写 `lastModifiedBy=当前操作人`、`lastModifiedAt=now`、`revision+1` |
| A3-5 | 一条证据关联 N 个对象时 N 个模块全部出现引用条目 | `by-object` 按 `(object_type, object_id)` 反查关联表，任一对象均可命中 |
| A3-6 | 解除关联后该模块引用条目消失，台账记录保留 | `DELETE /ledger/{id}/links/{linkId}` 只软删引用行 |
| A3-7 | 并发冲突以「最后保存时间」为准，并向被覆盖方发通知 | 后写生效（LWW）；`overwritesOtherSide()` 判定聚合窗口内另一方刚写入则发 `LEDGER_CHANGED` 通知（`extra.overwritten=true`） |
| A3-8 | 同步失败重试并记失败日志，重试成功后两侧一致 | `TransactionTemplate` 包裹单次尝试，最多 3 次、退避 100ms×N；彻底失败写 `SYNC_FAILED` 操作日志并返回明确错误 |

### A4 关联管理与变更通知

| AC | 要求 | 实现 |
|---|---|---|
| A4-1 | 单条证据关联上限 20 个，第 21 个被拦截 | `maxLinks=20`，前后端一致拦截 |
| A4-2 | 详情可见关联清单（类型/名称/编号）并可跳转 | `LedgerLinkVO` 含 `objectNo/objectName/jumpPath`；跳转按对象类型定位到对应模块列表并预置 `keyword` |
| A4-3 | 支持解除关联，引用消失、台账保留 | 见 A3-6 |
| A4-4 | 解除至 0 个关联时拦截并提示「至少保留 1 个关联对象」 | `validateLinks` 非草稿且 0 关联即报错；前端同步拦截 |
| A4-5 | 关联对象已删除/已作废显示「失效关联」并标注原因（已删除/已作废），不静默丢失、不报错中断 | `LedgerObjectResolverImpl`：行不存在或软删 → `DELETED`；状态命中 `OBSOLETE/VOID/CANCELLED/CANCELED/SCRAPPED/INVALID` → `OBSOLETE`；解析异常同样降级为失效并留 WARN 日志 |
| A4-6 | 新增关联后新对象模块 ≤ 1 分钟出现引用条目 | 实时反查，见 A3-1 |
| A4-7 | 台账侧修改通知模块责任人；模块侧修改通知台账责任人 | `LedgerNotifyService.notifyAll`（排除操作人自己） |
| A4-8 | 通知含变更对象、字段、前后值、操作人、时间、可点击链接 | `content_json = {ledgerNo, ledgerName, link, operator, time, changes:[{field,before,after,operator,time}]}` |
| A4-9 | 送达时延 ≤ 1 分钟 | 与业务写入同事务落库，天然即时 |
| A4-10 | 同一用户 5 分钟内对同一证据的多次变更聚合为 1 条（可展开明细） | `agg_key = 用户\|类型\|对象类型\|对象键\|窗口起点`，窗口 = `notifyAggregateMinutes`（默认 5）；明细追加进 `changes`，上限 100 条 |
| A4-11 | 按通知类型（新增/修改/删除/失效）分别设置开关，互不影响 | 5 类：`LEDGER_CREATED` / `LEDGER_CHANGED` / `LEDGER_DELETED` / `LEDGER_LINK` / `OBJ_INVALID`；未配置视为开启；`GET|PUT /ledger/notification/settings` |

### A5 检索、筛选与批量操作

| AC | 要求 | 实现 |
|---|---|---|
| A5-1 | 支持按证据类型、关联对象、责任人、时间范围等检索 | `LedgerQuery`：`type/ownerId/deptId/status/keyword/occurStart/occurEnd/uploadStart/uploadEnd/objectType/objectKeyword` |
| A5-2 | 多条件为「与」语义 | 查询条件全部 AND 拼接 |
| A5-3 | 起止时间 start ≤ end，违反时提示「起始时间不能大于结束时间」 | 前端 `validateRange` 在两个时间区间上拦截，文案与 AC 一致 |
| A5-4 | 关键字命中名称/编号/备注 | `keyword` 同时匹配 `name` / `ledger_no` / `remark` |
| A5-5 | 结果总数正确；单页默认 20，可切 20/50/100 | `PageResult.total`；`limits.pageSizes` |
| A5-6 | 批量上传：≤ 20 个文件合并为 1 条多附件证据 | `POST /ledger/batch-upload`（`files[]` + `meta`），一次调用生成 1 条台账 |
| A5-7 | 批量关联：≤ 100 条证据关联到同一对象 | `POST /ledger/batch-link`，`batchLinkMax=100`；超出前端先拦 |
| A5-8 | 批量操作返回成功数、失败数与逐条失败原因 | `BatchResultVO{successCount, failCount, failures:[{target,reason}], ledgerIds}` |
| A5-9 | 无匹配数据时显示「无匹配数据」 | 列表与资料汇总处均使用 `el-empty description="无匹配数据"` |

### A6 权限与审计

| AC | 要求 | 实现 |
|---|---|---|
| A6-1 | 查看人登录台账后不出现「新建/编辑/删除」按钮 | 前端按 `ledger:add` / `ledger:edit` / `ledger:delete` 控制按钮；模块侧证据面板仅在持有 `ledger:add` 时显示「录入证据」 |
| A6-2 | 直接输入 URL 访问被拦并提示「无权访问该页面」 | 路由 `meta.permission='ledger'` + 守卫 `canAccess` 沿 matched 链复核，整体拒绝并提示 |
| A6-3 | 越权尝试被记录 | 服务端 `@PreAuthorize` 拒绝 + 全局异常处理落 `sys_operation_log` |
| A6-4 | 每条记录有操作日志，含前后值与 IP | `LedgerAuditService`：`SYNC_FROM_*`、`DOWNLOAD`、`ADD`、`UPDATE`、`DELETE` 等显式写 `sys_operation_log`（`before`/`after` 在 `params` 内） |
| A6-5 | 日志只读，界面不提供修改/删除 | 前端仅展示；后端无日志写接口暴露给台账角色 |
| A6-6 | 日志保留 ≥ 3 年 | `logRetainDays=1095` |
| A6-7 | 可按证据编号查看完整变更轨迹 | `GET /ledger/trail/{ledgerNo}` → `{history[], logs[]}`；`history` 来自 `plm_entity_history(object_type='LEDGER', object_id=ledger_no)`，`logs` 来自 `sys_operation_log` |

---

## 5. 可配置项（`plm.ledger.*`）

| 配置键 | 默认 | 对应 AC |
|---|---|---|
| `max-attachments` | 20 | A1-4 / A5-6 |
| `max-file-size-mb` | 200 | A1-5 |
| `allow-exts` | 14 种（见 A1-6） | A1-6 |
| `max-links` | 20 | A4-1 / A4-4 |
| `batch-link-max` | 100 | A5-7 |
| `name-max` / `remark-max` / `version-max` | 100 / 500 / 20 | A2-3 |
| `notify-aggregate-minutes` | 5 | A4-10 |
| `log-retain-days` | 1095 | A6-6 |

---

## 6. 关联对象类型与同步模块清单

`object_type` 共 7 类，全部可被关联并在模块侧实时展示（对应 A3「同步范围」清单第 1~6 项）：

| 序号 | 同步范围清单项 | object_type | 业务表（业务键） | 模块侧挂载点 |
|---|---|---|---|---|
| 1 | 项目 | `PROJECT` | `plm_project`（`project_no`） | 研发项目 · 阶段门抽屉 |
| 2 | 需求 | `REQUIREMENT` | `plm_issue`（`issue_no`） | 问题与洞察 · 问题详情抽屉 |
| 3 | 零件/物料 | `MATERIAL` | `plm_material`（`part_no`，软删） | 物料主数据 · 物料详情「关联证据」页签 |
| 4 | BOM | `BOM` | `plm_bom`（`bom_no`） | 多级爆炸 · 行内「证据」 |
| 5 | 变更单（ECN） | `ECN` | `plm_ecn`（`ecn_no`） | 工程变更 · ECN 详情抽屉 |
| 6 | 任务/评审 | `TASK` | `plm_work_item`（`id`） | 工作台 · 我的待办「证据」 |
| 6 | 任务/评审 | `REVIEW` | `plm_project_node`（`id`） | 由台账侧关联与跳转（`/project/review`） |

清单可扩展：新增类型只需在 `LedgerObjectResolverImpl.REGISTRY` 注册表增加一行（表名、业务键列、编号列、名称列、状态列、是否软删），其余链路（检索、关联、失效判定、跳转）自动生效。

---

## 7. 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/ledger/list` | `ledger:view` | 台账/资料汇总处 组合筛选分页（A5-1~A5-5） |
| GET | `/ledger/{id}` | `ledger:view` | 详情（含附件与关联清单） |
| GET | `/ledger/by-object` | `ledger:view` | 模块侧证据区引用条目（A3-1/A3-5） |
| GET | `/ledger/options` | 登录 | 下拉项与边界值 |
| GET | `/ledger/objects` | 登录 | 关联对象检索 |
| GET | `/ledger/owners` | 登录 | 责任人候选（仅返回 id/姓名/账号/部门） |
| GET | `/ledger/trail/{ledgerNo}` | `ledger:view` | 按证据编号的完整变更轨迹（A6-7） |
| POST | `/ledger` | `ledger:add` | 新建证据 |
| PUT | `/ledger/{id}` | `ledger:edit` | 编辑证据 |
| POST | `/ledger/batch-upload` | `ledger:add` | N 个文件合并为 1 条台账（A5-6） |
| DELETE | `/ledger/{id}` | `ledger:delete` | 删除证据 |
| POST | `/ledger/{id}/attachments` | `ledger:edit` | 追加附件 |
| DELETE | `/ledger/{id}/attachments/{attachmentId}` | `ledger:edit` | 移除附件（保留台账） |
| GET | `/ledger/attachments/{id}/download` | `ledger:download` | 下载（记 DOWNLOAD 日志，A6-4） |
| GET | `/ledger/attachments/{id}/preview` | `ledger:view` | 在线预览（inline） |
| POST | `/ledger/{id}/links` | `ledger:edit` | 新增关联 |
| DELETE | `/ledger/{id}/links/{linkId}` | `ledger:edit` | 解除关联（A4-3） |
| POST | `/ledger/batch-link` | `ledger:edit` | 批量关联（A5-7/A5-8） |
| POST | `/ledger/sync` | `ledger:edit` | 模块侧回写台账（双向同步，A3-2） |
| GET | `/ledger/notification/list` | 登录 | 我的通知 |
| GET | `/ledger/notification/unread-count` | 登录 | 未读数 |
| GET | `/ledger/notification/{id}` | 登录 | 通知详情（含 changes） |
| POST | `/ledger/notification/read` / `read-all` | 登录 | 标记已读 |
| GET/PUT | `/ledger/notification/settings` | 登录 | 通知订阅开关（A4-11） |

通知接口只读写当前登录用户自己的数据，越权在服务层按 `user_id` 归属判定。

---

## 8. 权限模型

### 8.1 权限码（菜单种子 `database/45_ledger.sql` §8）

| 权限码 | 类型（id） | 含义 |
|---|---|---|
| `ledger` | 菜单（2100） | 「证据台账」一级菜单，同时作为前端路由 `meta.permission` |
| `ledger:ledger` / `ledger:repository` / `ledger:notify` | 子页（2101 / 2102 / 2103） | 台账录入 / 资料汇总处 / 变更通知 |
| `ledger:view` / `ledger:add` / `ledger:edit` / `ledger:delete` / `ledger:download` | 按钮（2105–2109） | 查看 / 新建 / 编辑 / 删除 / 下载 |

**id 段位与修复说明**：台账权限整体占用 **2100–2109** 高位段（2100 菜单、2101–2103 子页、2105–2109 按钮）。早期版本曾用 `109` / `1091` / `1092`，与 `database/03_schema_extend.sql` 的 `archive`(109) / `archive:view`(1091) / `archive:upload`(1092) 主键冲突，被 `ON CONFLICT (id) DO NOTHING` 静默吞掉，导致 `ledger` / `ledger:repository` / `ledger:notify` 从未落库，非 ADMIN 角色被路由守卫拦在台账页外。修复见 `database/47_permission_seed_repair.sql` §4–§6：按 `perm_code` 幂等补插上述权限码、把存量库中已落库的 `ledger:*` 行改挂回 `ledger` 菜单，并重新补授角色；该脚本对全新库（00→47）与已应用旧版 45 号的存量库均可重复执行。

### 8.2 角色矩阵（字典与菜单种子 `database/45_ledger.sql` §9）

| 角色 | data_scope | 授权 |
|---|---|---|
| LEDGER_ADMIN（台账管理员） | 1（全部） | 全部 9 个权限 |
| LEDGER_EDITOR（台账编辑） | 2（本部门及以下） | 除 `ledger:delete` 外全部 |
| LEDGER_VIEWER（台账查看） | 2 | `ledger` / 三子页 / `ledger:view` / `ledger:download` |
| ENGINEER（工程） | 沿用 | +`ledger:view/add/edit/download` |
| QUALITY（品质）、SALES（销售） | 沿用 | +`ledger:view/download`（只读） |

### 8.3 界面可见范围

行级范围在服务层按 `scopeDeptId`（部门及以下）与 `scopeUserId`（仅本人）收窄；超出范围的行不返回。查看人登录后台账页不出现「新建/编辑/删除」按钮（A6-1），直接输入 URL 由路由守卫拦截并提示「无权访问该页面」（A6-2）。

---

## 9. 审计与日志（A6-4 ~ A6-7）

每次写入都会留下两类痕迹：

1. **实体轨迹** `plm_entity_history`：`object_type='LEDGER'`、`object_id=<证据编号>`，按 `version` 记录变更类型、变更字段、快照与操作人，用于「按证据编号还原完整轨迹」。
2. **操作日志** `sys_operation_log`：显式写入的操作名（`ADD` / `UPDATE` / `DELETE` / `DOWNLOAD` / `SYNC_FROM_MODULE` / `SYNC_FAILED` 等）、操作人、IP、结果、错误信息，以及 `before`/`after` 前后值（存于 `params` 文本列）。

台账详情抽屉「变更轨迹」页签同时展示上述两类记录，时间倒序，只读。

---

## 10. 已知偏差与说明

1. **通知表落位**：变更通知的建表与代码（`plm_notification` / `plm_notification_setting` + `ledger` 包）随能力 A 一并落在 `database/45_ledger.sql` 与 `com.hjgd.plm.ledger` 下，而不是原计划的独立编号/B 线位置。原因是 A4 的 AC 直接把变更通知列为能力 A 的交付项，分线会造成一张表被两条能力线同时改。B 线若需要分类订阅报告，直接读这两张表即可，不需要迁移。
2. **BOM 列表的跳转筛选**：`/bom/list` 现有后端查询只支持按顶级料号与状态过滤，因此从台账点击 BOM 关联的「前往」会落到 BOM 列表但不带关键字过滤（其余模块均带关键字预置）。不影响 A4-2「可点击跳转到该对象」，如需精确到 BOM 编号需要后端补充查询参数。
3. **附件格式白名单**为 14 种录入/图纸/压缩常见格式；调阅记录、试验报告等如出现 `csv`/`txt` 类文本证据，需在 `plm.ledger.allow-exts` 中追加。
4. **单条证据只增不覆盖**：编辑时新增附件为追加语义，不替换原有附件；如需替换请先「移除」再「追加」。
