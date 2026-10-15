# 功能说明书（能力 B · 资料分类订阅与报告推送）

适用版本：恒剑光电 PLM V4.0（能力线 B，PAND-201 / 阶段 4）
日期：2026-10-14

---

## 1. 范围

本说明书覆盖能力线 B 的六个功能点，全部纳入本轮实现：

| 编号 | 功能点 | 交付内容 | 主要验收标准 |
|---|---|---|---|
| B1 | 资料分类维度与分类值管理 | 部门自定义维度、维度值上限、改名/停用、本部门内唯一、一条资料多维度标签、删除二次确认 | AC-B1-1 ~ B1-7 |
| B2 | 分类的部门隔离与权限 | 本部门可管理、越权拦截与留痕、共享维度、全局维度只读、部门调动即生效 | AC-B2-1 ~ B2-7 |
| B3 | 单条资料订阅与变更推送 | 详情页「订阅本条」、变更/附件推送、5 分钟聚合、退订、作废自动失效、查看权限校验 | AC-B3-1 ~ B3-8 |
| B4 | 按时间周期订阅 | 日/周/月粒度与推送时点、自然周期区间、无更新也出报告、暂停恢复、修改不补发、删除留档、上限 20 条/人 | AC-B4-1 ~ B4-9 |
| B5 | 报告生成与推送 | 到点自动生成、每条资料带链接、失败重试 3 次、成功/失败均记推送日志、最近 12 期可回溯并下载 Excel | AC-B5-1 ~ B5-8 |
| B6 | 资料汇总处查询与下载 | 组合检索（含分类多选）、列表字段、在线预览、下载与越权拦截、链接定位、与台账一致、空结果与分页 | AC-B6-1 ~ B6-9 |

不在本轮范围：分类维度的跨部门审批流；报告的定时归档与清理任务（报告按人保留 12 期在查询层限制，物理清理未实现）；短信/企业微信等站内与邮件以外的推送渠道。

---

## 2. 核心设计

### 2.1 分类体系：按部门隔离的一棵树，加两种「越界可见」

`plm_category_dim.dept_id` 决定归属：`dept_id = 本部门` 为部门自定义维度（可管理），`dept_id IS NULL AND is_global = 1` 为全局维度（全部部门只读可见），`is_shared = 1` 的部门维度对他部门「可选用、不可编辑」。

AC-B2-1/B2-2/B2-6 因此不是三条分散的 if，而是**同一个判定**：`requireManageableDim(dimId)` 只有「本部门自定义」一条通过路径，全局维度与他部门维度都在此被拒（`CategoryServiceImpl.java:417`），拒绝时统一走 `deny()` 记日志（`CategoryServiceImpl.java:446`、`:456`）。前端不重复实现这套规则：`GET /subscription/category/manage` 对每个维度下发 `editable`，界面只按该字段显隐编辑入口，不会出现「界面上能点、服务端拒绝」的不一致。

### 2.2 资料汇总处 = 证据台账的另一个视图（零副本）

`RepositoryServiceImpl` 刻意不写自己的 SQL，而是直接复用 `LedgerService.page(query)`，再补上分类标签与附件清单（`RepositoryServiceImpl.java:32`）。这样：

- AC-B6-9「仅检索到有权限范围内的资料」由台账的行级范围（`scopeDeptId` / `scopeUserId`，管理员不受限）**一次性**保证，汇总处不会另有一套越权面；
- AC-B6-7「台账侧变更后 ≤ 1 分钟在汇总处可见且为最新版本」不是靠同步任务争取的时延，而是**读的同一行**（与能力线 A 的 A3 同源）；
- AC-B6-2 的分类标签是查询时的补齐列（一次批量查 `plm_ledger_category` 再按 `ledger_id` 归并），不是台账行上的冗余字段，因此打标签**不产生**台账变更事件（见 §10.1）。

### 2.3 单条订阅：由台账写事件驱动，而不是轮询台账表

`ItemSubscriptionNotifier` 实现能力线 A 预留的扩展点 `LedgerChangeListener`，在台账写事务内收到 `LedgerChangeEvent` 后即时推送（`ItemSubscriptionNotifier.java:44`）。选事件而不是轮询，是因为**附件的追加/移除不改台账行的任何字段也不改时间戳**——轮询看不见这两类变更，而 AC-B3-2 明确要求「字段变更**或**附件更新」都触发推送；事件里还直接带着「改了哪个字段、改前改后各是什么」，正好是 AC-B3-3 要的前后值。

推送复用能力线 A 的 `LedgerNotifyService`（通知类型 `SUB_ITEM`），于是 AC-B3-4 的「5 分钟内聚合为 1 条」不是 B 线自己实现的一套窗口逻辑，而是 A 的 `agg_key`（用户 + 类型 + 对象 + 窗口起点）在生效——两条能力线对「聚合」的语义永远一致。

### 2.4 周期订阅：一条时间游标 + 每分钟扫一次

`plm_subscription.next_run_at` 是唯一的调度状态。`ReportScheduleJob` 每分钟扫一遍「`status = ENABLED` 且 `next_run_at <= now()`」的订阅（`ReportScheduleJob.java:44`），而不是给每条订阅注册一个定时任务：改周期、暂停、恢复都只需要**重算一个时间戳**，不必迁移已有任务（AC-B4-6/B4-7）。

报告的区间一律取「**上一个已结束的自然周期**」：按日取昨天 00:00~今天 00:00、按周取上周一~本周一、按月取上月初~本月初，左闭右开。这样「到期后立刻生成」与「报告覆盖一个完整周期」不再冲突，相邻两期的区间首尾相接且不重叠（AC-B4-2/B4-4）。

游标**无条件前移**：某一期即使生成失败也不再重试该期（失败已按 AC-B5-5 通知订阅人），否则一条坏数据会把该订阅永远卡在同一期，之后所有周期都发不出去。

---

## 3. 数据模型

新增 5 张表（`database/46_subscription.sql`），全部沿用全局的 `deleted` 软删约定：

### 3.1 plm_category_dim（分类维度）

| 字段 | 类型 | 说明 |
|---|---|---|
| `dept_id` | BIGINT | 归属部门；`NULL` = 全局维度（AC-B2-6） |
| `dim_code` | VARCHAR | 维度编码 |
| `dim_name` | VARCHAR(20) | 维度名称，≤ 20 字（AC-B1-1） |
| `is_global` | SMALLINT | 1 = 全局预置，任何部门只读（AC-B2-6） |
| `is_shared` | SMALLINT | 1 = 共享，他部门可选用不可编辑（AC-B2-4/B2-5） |
| `status` / `sort_order` | | 状态与排序 |

唯一索引 `uk_cat_dim_dept_name (dept_id, dim_name)` 保证本部门内维度名唯一（AC-B1-5）；因为 PG 的 UNIQUE 把 `NULL` 视为互不相等，全局维度另用 `uk_cat_dim_global_name` 单列一条约束。

### 3.2 plm_category_value（分类值）

`dim_id`、`value_code`、`value_name`（≤ 30 字）、`status`（`ENABLED` / `DISABLED`）。`uk_cat_value_name (dim_id, value_name)` 保证同维度内值名唯一。停用（`DISABLED`）后不再出现在任何新的选择项中，但已打在资料上的标签保留（AC-B1-4）。

### 3.3 plm_ledger_category（资料 ↔ 维度值标签）

`ledger_id`、`dim_id`、`value_id`。唯一索引 `uk_ledger_cat (ledger_id, dim_id)` ——**一条资料在每个维度上只允许一个值**，多维度并存由多行表达（AC-B1-6）。

### 3.4 plm_subscription（订阅，单条 + 周期统一表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` / `sub_no` | | 订阅人 / 订阅编号（`SUB-yyyyMMdd-NNNN`） |
| `type` | VARCHAR(8) | `ITEM` 单条（AC-B3）/ `PERIOD` 周期（AC-B4） |
| `name` | VARCHAR(64) | 订阅名称（列表展示；未填时按范围/编号自动生成） |
| `ledger_id` | BIGINT | 单条订阅指向的资料（AC-B3-1） |
| `filter_json` | JSONB | 周期订阅的分类条件 `{dims:[{dimId,valueIds:[…]}]}`（AC-B4-3） |
| `period` / `weekday` / `monthday` | | 粒度（`DAY`/`WEEK`/`MONTH`）与星期几/几号（AC-B4-1） |
| `push_time` | TIME | 推送时点，默认 `08:00`（AC-B5-1） |
| `status` | VARCHAR(8) | `ENABLED` / `PAUSED`（AC-B4-6） |
| `next_run_at` | TIMESTAMPTZ | 下次生成时点（调度游标，AC-B4-7/B5-1） |

唯一索引 `uk_subscription_item (user_id, ledger_id)` 只对单条订阅生效——同一用户对同一资料只有一条有效订阅，不同用户互不影响（AC-B3-6）。周期订阅上限 20 条/人为服务层计数（AC-B4-9）。

### 3.5 plm_report / plm_report_delivery_log（报告头与推送日志）

`plm_report`：`report_no`、`subscription_id`、`sub_name`（**订阅名快照**，订阅删除后仍可回溯，AC-B4-8）、`user_id`、`type`、`period_start` / `period_end`（报告覆盖区间）、`item_count`（去重后条数，AC-B4-4）、`content_json`（资料条目清单，含指向汇总处的链接，AC-B5-2）、`status`（`PENDING`/`GENERATED`/`PUSHED`/`FAILED`）、`retry_count` / `error`（AC-B5-5）、`generated_at` / `pushed_at`。`excel_file_id` 保留但恒为 `NULL`（见 §10.2）。

`plm_report_delivery_log`：`report_id`、`channel`（`INBOX`/`EMAIL`）、`result`（`SUCCESS`/`FAILED`/`SKIPPED`）、`target`（邮件收件地址）、`error`、`delivered_at`（AC-B5-6）。成功、失败、渠道未启用都留一行。

---

## 4. 功能点 × AC 对照

### B1 分类维度与分类值管理

| AC | 要求 | 实现与验证 |
|---|---|---|
| B1-1 | 部门管理员可新增维度，填名称（≤ 20 字）与值列表（每项 ≤ 30 字），创建后立即对该部门可用 | `POST /subscription/category/dim`；`dimNameMax=20` / `valueNameMax=30`（前后端双向拦截）；落库 `dept_id = 当前部门`，`GET /category/select` 立即下发 |
| B1-2 | 单部门维度上限 10，第 11 个被拦截并提示上限 | `maxDimsPerDept=10`；`CategoryServiceImpl.createDim` 先做重名校验再做计数校验，超过即报「分类维度已达上限…」 |
| B1-3 | 单维度值上限 50，第 51 个被拦截 | `maxValuesPerDim=50`；`addValue` 计数校验 |
| B1-4 | 维度值支持新增、改名、停用；停用后不出现在新选择项，历史标签保留且可查询 | `POST/PUT /category/dim/{id}/value[/{valueId}]` 支持 `valueName` 与 `status`；**刻意不提供删除值接口**——删除会让历史标签指向空值；`select` 只下发 `ENABLED` 值，`CategoryService.labelsOf` 不按值状态过滤，历史标签照常回显 |
| B1-5 | 维度名称在本部门内唯一，重复时报「维度名称已存在」 | `uk_cat_dim_dept_name` 唯一索引 + 服务层预检（先给出可读提示，唯一索引兜住并发） |
| B1-6 | 一条资料可同时打多个维度的标签，两条标签均保存并在详情可见 | `uk_ledger_cat (ledger_id, dim_id)`；`assign` 覆盖式提交（`items:[{dimId,valueId}]`），本期改为**先整批校验同维度重复再落库**（`CategoryServiceImpl.java:268-275`），保证「同一维度只能写一个分类值」是入参校验的结果而不是靠事务回滚 |
| B1-7 | 删除维度需二次确认；已被资料使用时提示影响条数，确认后标签从资料上移除 | 两段式：`GET /category/dim/{id}/usage` 回「N 个维度值 / 影响 M 条资料」→ 前端 `ElMessageBox.confirm` → `DELETE /category/dim/{id}?confirm=true`；删除级联清理 `plm_ledger_category` 标签并记 `CATEGORY_DIM_DELETE` 日志 |

### B2 分类的部门隔离与权限

| AC | 要求 | 实现与验证 |
|---|---|---|
| B2-1 | 部门 A 管理员仅能管理部门 A 的维度与值；对部门 B 的维度不显示编辑入口 | `requireManageableDim` 的唯一通过路径是「本部门自定义」；`manage` 只对本部门维度下发 `editable=true`，界面按该字段显隐按钮 |
| B2-2 | 越权调用他部门维度的接口/URL 被拒并提示「无权限」，目标数据不被修改 | `requireManageableDim` 抛 `FORBIDDEN`「无权限」；判定在**任何写操作之前**，被拒时无任何写入 |
| B2-3 | 越权尝试记入日志（操作人、时间、被拒操作、目标维度） | `deny()` → `LedgerAuditService.logDenied(operation, 维度名, 原因)`，动作码 `DENIED_CATEGORY_CROSS_DEPT` / `DENIED_CATEGORY_GLOBAL` / `DENIED_CATEGORY_USE` |
| B2-4 | 本部门维度对本部门可见；他部门分类默认不可见，除非标记「共享」 | `select`（打标签/筛选用）= 本部门 ∪ 共享 ∪ 全局；`manage`（管理用）= 仅本部门 |
| B2-5 | 维度标记「共享」后，他部门可选用但不可编辑其定义 | `requireUsableDim` 放行共享维度，`requireManageableDim` 不放行；`assign` 中他部门非共享维度报 `DENIED_CATEGORY_USE` |
| B2-6 | 全局维度对全部部门只读可见，任何部门管理员均不可修改 | 全局维度种子（「资料类型」）`dept_id IS NULL AND is_global = 1`；`updateDim`/`deleteDim` 在全局维度上报 `DENIED_CATEGORY_GLOBAL` |
| B2-7 | 用户跨部门调动后，可管理范围随当前所属部门即时变化，无需人工调整 | 部门一律在每次请求时从登录态解析（`myDeptId()`），不缓存、不落订阅/维度之外的任何地方；调动后重新登录即生效 |

### B3 单条资料订阅与变更推送

| AC | 要求 | 实现与验证 |
|---|---|---|
| B3-1 | 资料详情页提供「订阅本条」入口，点击后订阅生效、入口状态变「已订阅」 | 汇总处详情抽屉内「订阅本条」→ `POST /subscription/item?ledgerId=`；`GET /subscription/item/state?ledgerId=` 回 `{subscribed, subscriptionId, subNo}` 驱动入口态 |
| B3-2 | 订阅后该条发生字段变更或附件更新时，≤ 5 分钟内推送一次 | 台账写事务内同步推送（`ItemSubscriptionNotifier.java:44`），实际为即时（≤ 5 分钟是保守上界）；事件的 `Kind` 覆盖 `CREATED`/`UPDATED`/`ATTACH_ADDED`/`ATTACH_REMOVED` |
| B3-3 | 推送内容含资料名称、变更字段、前后值、变更人、变更时间、指向资料汇总处的链接 | 通知类型 `SUB_ITEM`，`changes` 为 A 的通知行结构（`field`/`before`/`after`），`extra` 含 `ledgerNo`/`ledgerName`/`operator`/`link`（`/ledger/repository?ledgerId=N`） |
| B3-4 | 同一订阅人对同一条资料 5 分钟内的多次变更聚合为 1 条；超过 5 分钟生成新通知 | 复用 `LedgerNotifyService` 的 `agg_key`（用户 + 类型 + 对象 + `notifyAggregateMinutes` 窗口起点，默认 5 分钟）；邮件与报告快照另用 `itemReportExistsSince(subscriptionId, windowStart)` 在同窗口内只放行第一份，避免刷屏 |
| B3-5 | 可退订单条订阅，退订后不再推送 | `DELETE /subscription/item?ledgerId=`，只删除当前用户的那一行；未订阅时静默成功 |
| B3-6 | 同一条资料可被多个用户分别订阅、互不影响 | `uk_subscription_item (user_id, ledger_id)`；`subscribersOf(ledgerId)` 返回全部有效订阅人，逐个独立 try/catch，一人推送失败不影响其他人 |
| B3-7 | 资料被删除或作废时推送一次「已作废/删除」通知，该订阅自动失效 | `LedgerChangeEvent.isVoidOrDelete()`（`Kind.DELETED` 或 `status=VOID`）→ 标题区分「已删除」/「已作废」→ 推一次后 `invalidateItemSubscriptions(ledgerId)` 删除全部单条订阅 |
| B3-8 | 无查看权限的用户不能订阅 | `create` 前调 `ledgerService.requireReadable(ledgerId)`（复用能力 A 的查看权判定，不另写一套） |

### B4 按时间周期订阅

| AC | 要求 | 实现与验证 |
|---|---|---|
| B4-1 | 可选日/周/月；按周可指定星期几、按月可指定每月几号 | `period` + `weekday`（1–7）+ `monthday`（**限定 1–28**，保证 2 月一定存在该日，不存在取不到的日期）；选周未给星期几、选月未给几号一律拒绝，不代填 |
| B4-2 | 按自然日/自然周（周一~周日）/自然月计算区间，不采用滚动窗口 | `SubscriptionService.periodOf()` 返回「上一个已结束周期」的左闭右开区间；周起点固定为周一 |
| B4-3 | 到期自动生成一次报告，覆盖「本周期内」新增或发生变更的资料 | `ReportItemMapper.selectPeriodItems`：`l.updated_at ∈ [start,end)` **∪** 附件 `updated_at ∈ [start,end)` **∪** `sys_operation_log` 的 `ATTACH_REPLACE`/`ATTACH_REMOVE` ∈ `[start,end)`（附件移除不改台账行，只看 `updated_at` 会漏） |
| B4-4 | 同一条资料在一个周期内多次变更只出现 1 次；跨周期各出现 1 次 | 周期查询按资料去重（同一 `ledger_id` 一行，合并本期全部变更）；区间左闭右开保证相邻两期不重叠、不重复计数 |
| B4-5 | 周期内无符合条件资料时仍生成并推送「本期无更新」报告 | `emptyPeriodStillProducesReport`：报告照常落库、照常推送，正文与明细区显示「本期无更新」，`item_count=0` |
| B4-6 | 可暂停订阅；暂停期间不生成，恢复后从恢复时刻所属的下一个完整周期开始 | `PUT /subscription/{id}/status`；`listDue` 只取 `status=ENABLED`；恢复时按当前时刻重算 `next_run_at`（历史期不补发） |
| B4-7 | 可修改周期粒度与推送时点，修改后从下一个完整周期起生效，不补发历史 | `update` 后 `next_run_at` 前移到下一完整周期；调度器只按游标生成，不回溯 |
| B4-8 | 可删除周期订阅；删除后不再生成报告，已发送的历史报告不受影响 | `delete` 只软删订阅行；报告表持有 `sub_name` 快照与 `subscription_id`，删除后仍可在「我的报告」查看与下载 |
| B4-9 | 列表展示订阅名称、周期、下次生成时间、状态；周期订阅上限 20 条/人，超限被拦截 | `SubscriptionVO` 含 `name`/`periodLabel`/`nextRunAt`/`status`；`maxPeriodPerUser=20` 在服务层计数拦截并提示 |

### B5 报告生成与推送

| AC | 要求 | 实现与验证 |
|---|---|---|
| B5-1 | 按设定时点自动生成并推送，偏差 ≤ 10 分钟（默认：按日 08:00、按周周一 08:00、按月 1 日 08:00） | `ReportScheduleJob` `@Scheduled(fixedDelay = 60_000)` 每分钟扫一次到期订阅 → 偏差 ≤ 1 分钟；默认时点为表单初值 `08:00` / 周一 / 每月 1 号（`subscription.vue` 的 `dlg` 初值） |
| B5-2 | 报告中每一条资料条目均含可点击链接，点击跳转并定位到资料汇总处该条 | `content_json` 每项带 `link`，HTML 正文与 Excel 明细同一份来源；链接形如 `/ledger/repository?ledgerId=N` |
| B5-3 | 链接跳转带权限校验，无权限时提示「无权限」且不返回文件内容与详情字段 | 链接落在汇总处，定位后打开详情/下载都走能力 A 的 `ledger:view` + `requireReadable`；无权限即被拒，不返回字段 |
| B5-4 | 从报告点击到目标资料详情渲染完成，操作步数 = 1 次点击 | 链接自带 `ledgerId`，汇总处加载完成后自动定位并高亮该行（不要求用户再次检索），与 AC-B6-6 同一实现 |
| B5-5 | 生成失败自动重试 3 次；仍失败则记失败日志并向订阅人发失败提示，不静默丢失 | `reportRetry=3` 的尝试循环；仍失败落 `status=FAILED` + `retry_count` + `error`，并推 `SUB_REPORT`「报告生成失败」通知；推送全渠道失败另有「报告推送失败」通知 |
| B5-6 | 推送成功/失败均记日志，含订阅 ID、周期区间、生成时间、推送渠道、结果 | 每次投递写 `plm_report_delivery_log`（`channel`/`result`/`target`/`error`/`delivered_at`），订阅 ID、周期区间、生成时间在报告头上（见 §10.6）；报告详情页把明细与推送记录并排展示 |
| B5-7 | 可查看本人最近 12 期历史报告，并按期下载 Excel | `page` 把 `pageSize` 上限压到 `reportRetain=12`；`GET /subscription/report/{id}/excel` 按需从 `content_json` 生成 `.xlsx` 字节流 |
| B5-8 | 单条订阅触发的推送，链接规则与 B5-2 一致 | 两种报告共用 `ledgerLink()` 与同一份正文/Excel 生成代码 |

### B6 资料汇总处查询与下载

| AC | 要求 | 实现与验证 |
|---|---|---|
| B6-1 | 可按分类维度（多选）、关键字、时间范围（上传/变更）、责任人、来源证据编号、关联对象单独或组合检索 | `GET /ledger/repository` 收 `LedgerQuery`：`categoryFilter`（同维度取或、跨维度取与，见 §6.1）、`keyword`、`uploadStart/uploadEnd`、`modifyStart/modifyEnd`、`ownerId`、`ledgerNoLike`、`objectType/objectKeyword`、`type`、`status`、`deptId` |
| B6-2 | 列表字段至少含资料名称、资料类型、分类标签、责任人、上传时间、来源证据编号、操作（查看/下载） | `RepositoryItemVO`：`ledgerNo`/`name`/`typeName`/`categories`/`ownerName`/`uploadedAt`/`lastModifiedAt`/`attachmentCount`/`files`（含 `previewable`/`downloadUrl`）/`link` |
| B6-3 | pdf 与图片可直接在页面内预览，其他格式提示下载查看 | 复用能力 A 的 `GET /ledger/attachments/{id}/preview`（inline）；`RepoFileVO.previewable` 由扩展名判定，前端对不可预览格式给出下载提示 |
| B6-4 | 仅对资料具备查看权限的用户可下载；无权限被拒并提示「无权限」 | 下载走 A 的 `GET /ledger/attachments/{id}/download`，权限码 `ledger:download` + `requireReadable`（行级范围） |
| B6-5 | 越权下载（含直接访问文件 URL）被拒并记日志（操作人、时间、目标资料、结果） | A 的下载接口在服务端做权限判定，并在每次下载写 `DOWNLOAD` 操作日志（A6-4 同一机制），直接访问文件 URL 同样经过该接口 |
| B6-6 | 从报告/订阅链接进入时定位到对应资料，不要求再次手工检索 | `?ledgerId=` → `LedgerQuery.ledgerId` 精确定位单条，列表高亮该行 |
| B6-7 | 汇总处与证据台账一致：台账侧新增/变更附件的资料 ≤ 1 分钟可在汇总处检索到且为最新 | 同一行读取，天然一致、天然最新（见 §2.2）；汇总处只额外拼分类标签与附件清单 |
| B6-8 | 空结果显示「无匹配数据」；单页默认 20，可切 20/50/100，总数正确 | `el-empty description="无匹配数据"`；`PageResult.total` 与 `limits.pageSizes` |
| B6-9 | 用户仅能检索到有查看权限范围内的资料，无权限资料不出现在列表与总数中 | 行级范围在 `LedgerService.page` 内部同时收窄列表与 `count`，范围外资料的既不返回也不计入总数 |

---

## 5. 可配置项（`plm.subscription.*`）

| 配置键 | 默认 | 对应 AC |
|---|---|---|
| `dim-name-max` / `value-name-max` | 20 / 30 | B1-1 |
| `max-dims-per-dept` | 10 | B1-2 |
| `max-values-per-dim` | 50 | B1-3 |
| `max-period-per-user` | 20 | B4-9 |
| `report-retain` | 12 | B5-7 |
| `report-retry` | 3 | B5-5 |
| `default-push-time` | `08:00` | B5-1 |
| `link-base-url` | 空（输出相对路径，由前端同源解析） | B5-2 |
| `schedule-enabled` | true | B4-3（关闭后调度器不生成，便于灰度/测试） |
| `mail-enabled` | false | B5 渠道（未配置 SMTP 时仅站内，邮件渠道记 `SKIPPED`） |
| `mail-from-name` | `恒剑光电 PLM` | 邮件主题前缀 |

---

## 6. 分类条件与订阅规则

### 6.1 分类筛选语义

一个 `CategoryFilter` 由若干 `{dimId, valueIds[]}` 分组组成：**同维度内多个值取「或」**（命中任一值即算命中该维度），**不同维度之间取「与」**（每个已选维度都要命中）。所以「资料类型 = 试验报告 且 产品线 = A 系列」才是一次有效筛选。空分组（未选任何值）在服务层被丢弃，不会把结果集清空。

- 汇总处检索（GET 查询串）：条件按参数名逐段展开为
  `categoryFilter.dims[0].dimId=5&categoryFilter.dims[0].valueIds=12&categoryFilter.dims[0].valueIds=13`。
  同名重复键即列表元素——`src/api/subscription.js` 的 `pageRepository` 显式声明 `paramsSerializer: { indexes: null }`，因为 axios 默认会把数组写成 `valueIds[]=12`，多出的 `[]` 会让后端的逐段绑定对不上，条件静默失效。
- 订阅条件（JSON 请求体）：直接提交 `{dims:[{dimId, valueIds:[…]}]}`，落库到 `plm_subscription.filter_json`，生成报告时原样转成查询条件。

两个入口共用同一张 `plm_ledger_category`，因此「汇总处筛出来的」与「报告里覆盖的」口径不会分叉。

### 6.2 周期与推送规则

| 粒度 | 可选参数 | 周期区间（报告覆盖） | 默认推送时点 |
|---|---|---|---|
| `DAY` | — | 昨天 00:00 ~ 今天 00:00 | 08:00 |
| `WEEK` | `weekday` 1–7（周一=1） | 上周 `weekday` 所在的自然周（周一 00:00 ~ 下周一 00:00） | 08:00 |
| `MONTH` | `monthday` 1–28 | 上一个自然月（上月初 ~ 本月初） | 08:00 |

- 订阅状态：`ENABLED`（启用，正常生成）/ `PAUSED`（暂停，不生成，游标冻结）。
- 修改粒度/时点后，游标前移到下一个完整周期，**不补发**历史期（AC-B4-7）。
- 停机期间错过的周期会逐期补发（每分钟扫描时循环前移游标）；积压超过 `report-retain` 期时直接跳到当前时间之后——补发几十封历史报告不是订阅人要的。

### 6.3 报告格式、内容与权限

**报告内容**（`content_json` 与 HTML/Excel 同源）：每条资料含资料名称、资料类型、分类标签、责任人、来源证据编号（`ledgerNo`）、上传/变更时间、**指向资料汇总处该条资料的链接**。

**推送渠道**：站内通知（`INBOX`）+ 邮件（`EMAIL`）。邮件渠道需配置 SMTP 并置 `mail-enabled=true`，未配置时该渠道记 `SKIPPED` 而不报错。

**权限**：报告只属于订阅人（服务层按 `user_id` 判定，越权返回 `NOT_FOUND`/`FORBIDDEN`）；报告内的链接点击后按被点资料的查看权限再校验一次（AC-B5-3），**订阅不会给用户带来任何额外可见范围**。

**保留**：「我的报告」按人保留最近 12 期（查询层限制），可查看详情、HTML 正文与下载 Excel。

---

## 7. 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/subscription/category/select` | `category:view` | 可用的分类维度与值（本部门 + 共享 + 全局，仅 `ENABLED` 值） |
| GET | `/subscription/category/manage` | `category:view` | 分类管理页数据（含 `editable`） |
| GET | `/subscription/category/dim/{id}/usage` | `category:view` | 维度使用情况（删除前的影响条数，B1-7） |
| POST | `/subscription/category/dim` | `category:manage` | 新建维度（B1-1/B1-2） |
| PUT | `/subscription/category/dim/{id}` | `category:manage` | 改名 / 共享 / 排序 |
| DELETE | `/subscription/category/dim/{id}?confirm=` | `category:manage` | 删除维度，`confirm=false` 只回影响提示（B1-7） |
| POST | `/subscription/category/dim/{id}/value` | `category:manage` | 新增分类值（B1-3） |
| PUT | `/subscription/category/dim/{id}/value/{valueId}` | `category:manage` | 改名 / 停用启用（B1-4） |
| POST | `/subscription/category/ledger/{ledgerId}` | `ledger:edit` | 为资料打标签（覆盖所选维度，B1-6） |
| GET | `/ledger/repository` | `ledger:view` | 资料汇总处组合检索（B6-1 ~ B6-9） |
| GET | `/subscription` | `subscription:view` | 我的订阅列表（B4-9） |
| POST | `/subscription` | `subscription:manage` | 新建订阅（单条/周期） |
| PUT | `/subscription/{id}` | `subscription:manage` | 修改订阅（B4-7） |
| PUT | `/subscription/{id}/status?status=` | `subscription:manage` | 暂停/恢复（B4-6） |
| DELETE | `/subscription/{id}` | `subscription:manage` | 删除订阅（B4-8，历史报告保留） |
| GET | `/subscription/item/state?ledgerId=` | `subscription:view` | 详情页订阅状态（B3-1） |
| POST | `/subscription/item?ledgerId=` | `subscription:manage` | 订阅本条（B3-1/B3-8） |
| DELETE | `/subscription/item?ledgerId=` | `subscription:manage` | 退订单条订阅（B3-5） |
| GET | `/subscription/report` | `report:view` | 我的报告（最近 12 期，B5-7） |
| GET | `/subscription/report/{id}` | `report:view` | 报告详情（报告头 + 资料明细 + 推送记录，B5-6） |
| GET | `/subscription/report/{id}/html` | `report:view` | 报告 HTML 正文（站内与邮件共用） |
| GET | `/subscription/report/{id}/excel` | `report:download` | 下载报告 Excel 明细（B5-7） |

资料汇总处的**预览/下载**不另开接口，复用能力线 A 的 `/ledger/attachments/{id}/preview` 与 `/ledger/attachments/{id}/download`（B6-3/B6-4/B6-5）。

---

## 8. 权限模型

### 8.1 权限码（`database/46_subscription.sql` §10）

| 权限码 | 类型（id） | 含义 |
|---|---|---|
| `subscription` | 菜单（2000） | 「订阅与报告」一级菜单，同时作为前端路由 `meta.permission` |
| `subscription:category` / `subscription:sub` / `subscription:report` | 子页（2001 / 2002 / 2003） | 分类管理 / 我的订阅 / 我的报告 |
| `subscription:view` / `subscription:manage` | 按钮（2005 / 2006） | 查看订阅 / 管理订阅 |
| `category:view` / `category:manage` | 按钮（2007 / 2008） | 查看分类维度 / 管理本部门分类 |
| `report:view` / `report:download` | 按钮（**1126** / 2010） | 查看报告 / 下载报告 |

**id 段位与修复说明**：订阅与报告权限占用 **2000–2010** 高位段（2000 菜单、2001–2003 子页、2005–2010 按钮）。早期版本曾用 `111` / `1110`–`1120`，其中 1110–1120 已被 `database/36_ebms_users_roles.sql` 占用——36 号未指定 id，由 `sys_permission_id_seq` 自增得到：1110=inquiry:import、1111=inquiry:export、1112=product:view、1115=product:delete、1116=customer:view、1117=customer:create、1118=customer:edit、1119=customer:delete、1120=material:view；冲突被 `ON CONFLICT (id) DO NOTHING` 静默吞掉，`category:view`/`category:manage`/`report:download`/`subscription:category`/`subscription:sub`/`subscription:report`/`subscription:view`/`subscription:manage` 均未落库，对应接口在 `@PreAuthorize` 上对全部角色（含 ADMIN）返回 403。修复见 `database/47_permission_seed_repair.sql` §1–§3：按 `perm_code` 幂等补插缺失权限码并重新补授角色。

`report:view` 是其中唯一的例外：它已由 36 号脚本以 `id=1126`（`parent_id=0`）落库为同名权限码，46 号脚本因改用 `ON CONFLICT (perm_code) DO NOTHING` 而把它整行跳过，故其实际 id 仍是 **1126**，并非上表中的 2009。

### 8.2 角色矩阵

| 角色 | `data_scope` | 授权 |
|---|---|---|
| `CATEGORY_ADMIN`（部门分类管理员，新增） | 2（本部门） | 全部 10 个权限（含 `category:manage`） |
| `LEDGER_ADMIN` | 1（全部） | 订阅与报告全量 + `category:view`（**不含** `category:manage`，分类维护归部门分类管理员） |
| `LEDGER_EDITOR` / `LEDGER_VIEWER` / `ENGINEER` | 沿用 | 订阅与报告 + `category:view` |
| `QUALITY` / `SALES` | 沿用 | 订阅与报告 + `category:view` |
| `ADMIN` | 1 | 全量（种子按 `perm_code` 补授，含 2000–2010 段） |

### 8.3 界面与行级范围

- 路由 `/subscription` 组 `meta.permission = 'subscription'`：未授权用户直接输入 URL 由路由守卫沿 `matched` 链整体拒绝并提示「无权访问该页面」。
- 分类管理页的编辑入口由服务端下发的 `editable` 驱动（不重复实现部门判定）。
- 资料汇总处的行级范围沿用台账：普通用户仅见「本部门 ∪ 本人上传」，台账管理员不受限；范围外资料既不出现在列表也不计入总数（B6-9）。

---

## 9. 审计与日志

| 场景 | 落点 | 内容 |
|---|---|---|
| 越权管理/使用分类维度（B2-3） | `sys_operation_log` | `DENIED_CATEGORY_CROSS_DEPT` / `DENIED_CATEGORY_GLOBAL` / `DENIED_CATEGORY_USE` + 操作人、时间、维度名、原因 |
| 删除分类维度（B1-7） | `sys_operation_log` | `CATEGORY_DIM_DELETE` + 维度名与删除前快照 |
| 为资料打标签（B1-6） | `sys_operation_log` | `CATEGORY_ASSIGN` + 证据编号 + 变更前后的标签集合 |
| 报告投递（B5-6） | `plm_report_delivery_log` | 每次渠道投递一行：渠道、结果（成功/失败/跳过）、送达对象、失败原因、时间 |
| 报告生成失败（B5-5） | `plm_report` + 站内通知 | `status=FAILED`、`retry_count`、`error`，并推 `SUB_REPORT` 失败通知 |
| 资料下载（B6-5） | `sys_operation_log` | 沿用能力 A 的 `DOWNLOAD` 日志（操作人、时间、附件、结果） |

---

## 10. 已知偏差与说明

1. **打分类标签不产生台账变更事件**。分类标签是汇总处查询时补齐的附加列（§2.2），不写台账行，因此不会触发 `LedgerChangeEvent`——订阅人不会因为别人给资料打了标签而收到「资料已变更」通知。AC-B3-2 的「字段变更或附件更新」按台账字段与附件理解，不含分类标签；若业务上需要把分类变更也推送，应在 `CategoryServiceImpl.assign` 末尾补发一次事件，而不是把标签落进台账行。

2. **报告 Excel 按需生成，不落盘**。`plm_report.excel_file_id` 保留但恒为 `NULL`：报告明细本身就是 `content_json` 快照，落盘会产生第二份需要单独清理和鉴权的字节。只要 `content_json` 非空（含「本期无更新」的空明细报告），Excel 随时可下载。代价是每次下载都重新生成一次（报告最大 12 期/人、明细为单周期条目，开销可忽略）。

3. **邮件渠道默认关闭**。`mail-enabled=false`，且 `MailReportSender` 依赖 `ObjectProvider<JavaMailSender>` ——未配置 SMTP 时该 bean 不存在，渠道降级为「记录 `SKIPPED` 并继续」，不会因为邮件不可用导致整份报告被判失败。AC-B5-6 的「成功/失败均记录」因此扩展为「成功/失败/跳过均记录」。

4. **单条订阅报告里的 INBOX 渠道记 `SKIPPED`**。单条订阅的站内通知已由变更推送（B3-2）在同一个 5 分钟窗口内发出，报告再发一条站内通知等于同一件事在一个窗口里出现两条。因此单条报告只走邮件与留档，站内渠道显式记 `SKIPPED`（可见、可查，不是静默丢弃）。

5. **AC-B5-1 的「默认时点」是表单初值**。按日 08:00 / 按周周一 08:00 / 按月每月 1 日 08:00 体现在新建周期订阅表单的初值上（`subscription.vue` 的 `dlg` 初值 `weekday: 1, monthday: 1, pushTime: '08:00'`）；服务端只强制「选周必须给星期几、选月必须给 1–28 的日期」与 `HH:mm` 格式，**不代填**。这样用户改过的值不会被服务端悄悄覆盖。

6. **推送日志以 `report_id` 关联报告**，订阅 ID、周期区间、生成时间不冗余进日志行。理由：这三个值属于报告头（且 `sub_name` 是快照），冗余一份到日志会出现两处可能不一致的快照；报告详情页把报告头、明细与推送记录并排返回，AC-B5-6 要的四要素一次看全。

7. **资料汇总处的预览/下载复用能力线 A 的附件接口**，未另开一套路由。B6-3/B6-4/B6-5 的「可预览格式」「下载权限」「越权留痕」由 A 的 `previewable` 判定、`ledger:download` + `requireReadable` 与 `DOWNLOAD` 日志保证；好处是汇总处与台账对「谁能看哪份文件」永远不会有两套判定。

8. **分类维度的新增/改名/共享未逐条落 `sys_operation_log`**。本轮只对「删除维度」（影响资料标签）与「越权尝试」记日志。分类维度属配置项、有 `updated_at`/`updated_by` 与软删留痕；若质检要求配置项也逐条留审计，可在 `createDim`/`updateDim` 各补一行 `auditService.log`。

---

## 11. 自检与验证

- 后端：`mvn -o test` —— 466 个用例全部通过（其中能力线 B 新增 76 个：`SubscriptionServiceImplTest` 25、`CategoryServiceImplTest` 23、`ItemSubscriptionNotifierTest` 11、`ReportServiceImplTest` 13、`CategoryFilterBindingTest` 4）。
- 前端：`npx vitest run` —— 45 个用例中 44 通过；唯一失败为 `api.test.js` 中「feedback API」用例与当前 `src/api/feedback.js` 的函数命名不一致，属**既有失败**（本轮未改动 `src/api/feedback.js` 与 `src/__tests__/api.test.js`）。
- 前端构建：`npm run build` 成功（11.7s）。
- 契约测试：`CategoryFilterBindingTest`（后端：参数名 → `LedgerQuery` 对象路径）与 `subscription.test.js`（前端：真实 axios 序列化 → 同名重复键）互为两端，钉住分类筛选的 GET 参数名契约。
