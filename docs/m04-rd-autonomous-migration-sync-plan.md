# M04 研发自治中心 · 迁移与双向同步总方案

> 文档编号: HJ-PLM-M04-MIG-SYNC-v1.0
> 上位基线: AEOS V7.2（`AEOS-` 仓库）/ System Registry V1 / Data Ownership Matrix V1 / Contract Pack V1
> 来源系统: **EBMS** `https://github.com/futianyu760118-art/EBMS.git`（Node/Express + SQLite，:3010）
> 目标系统: **--PLM-2** `https://github.com/futianyu760118-art/--PLM-2.git`（Java Spring Boot 3 + PostgreSQL + Vue3，:8080/:5173）
> 状态: **评审通过**（v1.0 冻结，见「零、评审结论」）
> 评审确认口径: 双主可写 · 双向实时同步 · 仅研发相关模块迁入 PLM-2（不回补 EBMS） · 原生 Vue3 · 磨合后再做模块调整

---

## 零、评审结论（v1.0 冻结）

依 AEOS 建议逐项决策，并收敛范围为「仅研发相关」：

| 决策点 | 决议 |
|---|---|
| D1 EBMS 独有节点 | ✅ **新增**（外观 APPEARANCE / 结构 STRUCTURE / 电子 ELECTRONICS），PLM-2 节点由 19 → **22** |
| D2 订单汇总 `order_summaries` | ❌ **不迁**（属财务/销售，非研发）；BOM 仅迁结构（EBMS 三态 → EBOM/MBOM/SBOM） |
| D3 规格书/配置表 与 参数模板 | ✅ 独立表 `plm_spec_sheet` / `plm_config_sheet` / `plm_product_config`，以 `part_no` 关联，保留为研发设计输出物 |
| D4 合规自检 `compliance` | ❌ **不新建表、不迁页面**（非研发，属 M02 数据治理）；如需仅复用 `dq`/`issue` 映射（不落独立表） |
| D5 冲突默认权威 | ✅ 产品/项目/BOM → PLM-2 优先；样品/规格/配置 → 过渡期 EBMS 优先；节点字段级（见 4.4） |
| D6 磨合期 | ✅ **6 个月**（退出条件：冲突率<1%、对账一致率≥99.5%、P4 验收通过） |
| D7 `test` 模块 | ❌ **不迁**（EBMS 开发自测工具，非研发业务对象） |

**范围收敛（"和研发无关的不用新建和迁移"）**：

| 处理 | 模块 |
|---|---|
| ✅ 迁入（研发相关） | 产品、研发项目、进度节点(22)、立项、复盘、供应链异常、销售推广、BOM(含类型/问题)、样品、规格书、配置表、技术转移 |
| ⛔ 不迁（非研发） | 合规自检、自动化测试、订单汇总/核价/询价/客户/订单（销售财经域）、物料/采购/供应商（交付域） |
| 🔁 仅映射（不新建） | 合规问题 → 复用 `plm_dq_debt` / `plm_issue`（如后续需要） |

---

## 一、背景与目标

### 1.1 目标

1. 将 EBMS 中「研发自治中心 M04」的全部分量与功能**迁入 PLM-2**，以 PLM-2 技术栈（Java + PostgreSQL + Vue3）**原生重建**。
2. 过渡期内 **EBMS 与 PLM-2 同时存在、各自可操作**：研发数据变更在两边**双向实时同步**，任一端均可读写。
3. 磨合期（暂定 6 个月，可评审调整）后，按 AEOS 边界将 EBMS 研发能力转为**只读/下线**，PLM-2 成为 M04 唯一事实源。

### 1.2 AEOS 原则（本方案遵循）

| 原则 | 来源 | 本方案落点 |
|---|---|---|
| M04 拥有 Product/Requirement/Drawing/BOM/ECO/Release | `AEOS/architecture/DATA_OWNERSHIP_MATRIX_V1.md:9` | 目标态由 PLM-2 承担 M04 |
| M03 EBMS 不再新增专业 CRUD，只做 Result/Exception/Decision/Action/Evidence | `EBMS/docs/AEOS_V7.2_EBMS_DOMAIN_BOUNDARY_V1.md:29` | 过渡期后 EBMS 研发模块只读 |
| 禁止跨域直接写表，跨域靠 Contract/Event | `AEOS/contracts/CONTRACT_PACK_V1.md:21` | 同步一律走 API/Event 契约，不直连对端库 |
| 闭环 Fact → Event → Decision → Action → Result → Verify → Evidence | `AEOS/contracts/CONTRACT_PACK_V1.md:35` | 事件契约与证据（Evidence）建模 |
| 先逻辑拆分 → 契约拆分 → API/Event 拆分 → Data Ownership 拆分 → 物理拆分 | `EBMS_DOMAIN_BOUNDARY_V1.md:38` | 见第九章分期 |
| 每模块唯一 Accountable Owner；以闭环/证据验收 | `AEOS/README.md:26` | 见第十一章 |
| 不新增 M13/一级编号；DEH 是运行链 | `AEOS/architecture/DEH_BOUNDARY_V1.md:10` | 同步中枢不新增一级系统，定位为 M11 连接器 + M02 证据 |

### 1.3 与 AEOS 的偏差登记（技术债，必须跟踪）

> 依 `CONTRACT_PACK_V1.md` 与 `EBMS_GUARDRAILS_V1.md`，任何"是否新增第二套业务事实源 / 跨域直写"为"是"的变更**默认不合并**，须登记迁移理由、Owner 与到期日。本方案因决策为**双主可写**，存在以下偏差，集中登记：

| 编号 | 偏差 | 风险 | 缓解措施 | 到期日 |
|---|---|---|---|---|
| DEBT-M04-001 | 过渡期出现**第二事实源**（EBMS 与 PLM-2 双主可写） | 数据分叉、指标多口径 | 对象级权威字段表 + 冲突记录表 + 每 15min 对账 + 强制 `correlation_id` 溯源 | 磨合期结束（T+6M） |
| DEBT-M04-002 | EBMS 研发模块**不回补/不重构** | EBMS 侧债务延续 | 仅做最小适配（接收/发送同步），不新增专业 CRUD | T+6M 转只读 |
| DEBT-M04-003 | 未做物理拆分，同步为逻辑关联 | 性能与耦合 | 同步走契约、限流、增量；物理拆分留 P4 | T+6M |
| DEBT-M04-004 | PLM-2 缺 `correlation_id` / HTTP 幂等键 | 重复入账、溯源断链 | 本方案新增 `correlation_id` + 幂等表（第 4.7 节） | P1 前完成 |

---

## 二、范围与模块清单

EBMS「研发自治中心 M04」= 11 个功能模块（后端 11 路由 + 前端 11 页面）。

| # | 模块 | EBMS 路由 | EBMS 主表 | PLM-2 现状 | 目标 PLM-2 模块 | 优先级 |
|---|---|---|---|---|---|---|
| 1 | 产品管理 | `/api/products` | products, product_bom | material（部分覆盖） | material 扩展 | P1 |
| 2 | 研发项目 | `/api/projects` | projects, rd_project_* | project/initiation/node（P1 已迁） | project 对齐 | P1 |
| 3 | 进度跟踪 | 同上 | rd_project_progress（22 节点） | plm_project_node（19 节点） | project 节点差异对齐 | P1 |
| 4 | 供应链异常/销售推广/复盘 | 同上 | rd_supply_issues / rd_sales_promotion / rd_project_reviews | 未实现 | project 子表新增 | P1 |
| 5 | BOM 明细/订单汇总 | `/api/bom` | bom_items, order_summaries | plm_bom/plm_bom_item（EBOM/MBOM/SBOM） | bom 扩展 | P2 |
| 6 | BOM 类型/类型化 BOM | `/api/products/bom-types` | bom_types, bom_typed_items | 无（三态部分等价） | bom 扩展 | P2 |
| 7 | BOM 问题闭环 | `/api/products/bom-issues` | bom_issues, bom_issue_tracks, bom_issue_preventions | 无 | bom 子模块新增 | P2 |
| 8 | 样品管理 | `/api/samples` | samples | 无 | sample 新增 | P2 |
| 9 | 规格书库 | `/api/spec-library` | spec_sheets, config_sheets | 无独立模块（SPEC/CONFIG 节点+参数模板） | spec 新增 | P2 |
| 10 | 产品配置表 | `/api/configs` | product_configs | 无（参数模板部分等价） | config 新增 | P2 |
| 11 | 技术转移 | `/api/tech` | tech_documents(+versions), tech_transfer_flows, handovers, changes, reviews, cases, access_logs | 仅 P4 规划 | tech 新增（含文件/水印/审批） | P3 |
| 12 | 合规自检 | `/api/compliance` | compliance_reports, compliance_issues | dq + improve（部分等价） | ⛔ **不迁**（非研发，仅按需映射 dq/issue） | — |
| 13 | 自动化测试 | `/api/test` | test_bugs, test_reports, test_module_status | 无 | ⛔ **不迁**（开发工具，非 M04 业务） | — |

> 说明：模块 2/3/4 的「研发项目进度」PLM-2 已按 `docs/plm-project-progress-tracking-spec.md` P1 实施，本方案负责**差异对齐与双向同步**，不重复建设。
> 模块 13（test）为 EBMS 的开发自测工具，非研发业务对象，**不迁入**，登记为范围外。

### 2.1 明确不做（范围外）

- 财务成本核算、库存/ERP 实际出入库（仅预留接口）。
- EBMS 侧功能增强/重构（依 DEBT-M04-002）。
- 非 M04 的 EBMS 模块（销售/采购/交付/财经等，归 M05/M06/M09）。

---

## 三、总体架构

### 3.1 双主 + 同步中枢

```
┌────────────────────────────┐            ┌─────────────────────────────────┐
│  EBMS (:3010)              │            │  PLM-2 (:8080 / :5173)          │
│  Node/Express + SQLite     │            │  Spring Boot 3 + PostgreSQL     │
│  ┌──────────────────────┐  │  双向同步   │  ┌───────────────────────────┐  │
│  │ M04 研发模块(11)      │◄─┼───────────┼─►│ M04 研发模块(原生 Vue3)    │  │
│  │ (过渡期 Legacy 可写)  │  │  契约/事件  │  └───────────────────────────┘  │
│  └──────────────────────┘  │            │  ┌───────────────────────────┐  │
│  ┌──────────────────────┐  │            │  │ Sync Hub 同步中枢(M11/M02)│  │
│  │ ext-sync Adapter     │  │            │  │ Inbox/Outbox/冲突/对账     │  │
│  └──────────────────────┘  │            │  └───────────────────────────┘  │
└────────────────────────────┘            └─────────────────────────────────┘
        ▲                                              ▲
        │        Contract: Object / API / Event         │
        └──────────────────────────────────────────────┘
              Fact → Event → Decision → Action → Result → Evidence
```

**定位**：同步中枢不新增 AEOS 一级编号，归属 **M11（连接器/Event Bus）+ M02（Event/Evidence/Audit）** 能力，部署于 PLM-2 内部，作为一个模块 + 定时 Job。

### 3.2 同步三条通道

| 通道 | 方向 | 载体 | 频率 | 用途 |
|---|---|---|---|---|
| A 实时事件 | EBMS→PLM-2 | EBMS 变更 → PLM-2 `POST /v1/sync/inbound` | 近实时 | 业务变更同步 |
| B 实时事件 | PLM-2→EBMS | `plm_domain_event`(Outbox) → Webhook → EBMS `/api/external-sync/*` | 30s 轮询投递 | 业务变更同步 |
| C 定时对账 | 双向 | 增量比对（`updated_at` + `revision` + `checksum`） | 每 15min + 每日全量 | 兜底、纠偏、冲突发现 |

### 3.3 关键技术选择

| 项 | 选择 | 理由 |
|---|---|---|
| 传输 | HTTPS + JSON + JWT/HMAC | 复用 PLM-2 `plm_webhook_subscription`（HMAC-SHA256）与 EBMS `external-sync` |
| 事件 | PLM-2 `plm_domain_event`（Outbox） + EBMS 变更日志 | PLM-2 已有成熟 Outbox/Webhook（`OutboxPublisherJob`） |
| 拉取 | PLM-2 `GET /v1/integration/events/pending` | 已有接口，供 EBMS 主动消费 |
| 幂等 | `object_type + source_system + external_key` 唯一 + `event_id` 唯一 | 防重复入账 |
| 溯源 | 新增 `correlation_id` / `trace_id` | 跨系统链路追踪（当前缺口） |
| 对账 | PLM-2 `query`/`exportx` + EBMS 只读接口 | 复用已有引擎 |

---

## 四、同步中枢设计（信建）

### 4.1 新增数据库表（PLM-2，PostgreSQL）

```sql
-- 对端系统注册
plm_sync_peer (
  id, system_code,           -- EBMS
  base_url, auth_type, auth_secret,
  direction,                 -- BIDIR / IN / OUT
  enabled, heartbeat_at, last_cursor, created_at, updated_at
)

-- 对象映射（跨系统同一对象的身份对应）
plm_sync_object (
  id, object_type,           -- PRODUCT/PROJECT/PROJECT_NODE/BOM/BOM_ITEM/SAMPLE/SPEC/CONFIG/TECH_DOC/...
  external_key,              -- 业务唯一键（part_no / project_no / doc_no ...）
  source_system, source_id,
  target_system, target_id,
  revision,                  -- 当前已同步版本
  checksum,                  -- 内容哈希(md5)
  last_synced_at, updated_at,
  UNIQUE(object_type, external_key)
)

-- 入站队列（EBMS→PLM-2）
plm_sync_inbox (
  id, event_id UUID UNIQUE, correlation_id,
  object_type, external_key, operation, -- CREATE/UPDATE/DELETE
  source_system, source_revision, payload_json JSONB,
  status,                    -- NEW/APPLIED/CONFLICT/FAILED
  error, received_at, applied_at
)

-- 出站 = 复用 plm_domain_event，新增列（correlation_id, source_system, revision, external_key）

-- 冲突记录
plm_sync_conflict (
  id, object_type, external_key, field_name,
  local_value, remote_value, local_ts, remote_ts,
  local_revision, remote_revision,
  resolution,                -- PENDING/LOCAL/REMOTE/MERGED
  resolved_by, resolved_at, note, created_at
)

-- 对账水位
plm_sync_checkpoint (
  id, system_code, object_type, cursor, last_run_at, lag_count
)
```

### 4.2 幂等与溯源字段（所有同步对象）

依 AEOS `CONTRACT_PACK_V1.md` Object/Event Contract 最小字段，PLM-2 各 M04 表补充（过渡期用扩展列或统一 `ext_json`）：

| 字段 | 含义 | 说明 |
|---|---|---|
| `source_system` | 最后写入来源 | EBMS / PLM2 |
| `external_key` | 跨系统业务键 | 已有（part_no/project_no/doc_no） |
| `revision` | 版本号（每次变更 +1） | 冲突检测 |
| `correlation_id` | 跨系统链路 ID | 新链路生成，贯穿双向 |
| `sync_status` | SYNCED/PENDING/CONFLICT | 同步状态标记 |
| `updated_at` | 最后变更时间 | LWW 兜底依据 |

### 4.3 变更捕获

- **PLM-2 侧**：AOP/Service 层在 M04 对象写操作后 `DomainEventService.publish("<obj>.<op>", ...)`，事件落 `plm_domain_event`。
- **EBMS 侧**：在 `db.js` 写路径外挂"变更钩子"（或在各 M04 路由写操作后统一埋点），生成变更事件 → 推送 PLM-2；EBMS 原生**无 Outbox**，此为 EBMS 侧唯一新增代码（最小适配，属 DEBT-M04-002 例外，需登记）。

### 4.4 冲突解决策略（双主可写，核心）

依 AEOS "结果可验证"要求，冲突策略按对象/字段定义：

1. **对象级权威表**（谁优先）：

| 对象 | 默认权威 | 冲突回退策略 |
|---|---|---|
| 产品/物料主数据（PART） | PLM-2 | LWW（revision 大者胜）→ 记冲突 |
| 研发项目（PROJECT） | PLM-2 | LWW → 记冲突 |
| 进度节点（PROJECT_NODE） | 双方可写 | **字段级**：日期/证据 PLM-2 优先，状态文本 EBMS 优先；同字段冲突记冲突 |
| BOM 结构 | PLM-2 | LWW → 记冲突 |
| 样品/规格/配置 | EBMS（过渡期） | LWW → 记冲突 |
| 技转文档 | PLM-2 | 文件以 PLM-2 为准，元数据 LWW |

2. **冲突判定**：同一 `object_type+external_key` 的两端 `revision` 不满足"后写入基于前版本"→ 视为冲突。
3. **冲突处理**：写入 `plm_sync_conflict`（PENDING），**不覆盖**权威值；提供前端「同步冲突」页人工裁决（选本地/远端/合并）。
4. **兜底**：无冲突且 revision 领先者直接应用（LWW）；每日全量对账发现分叉 → 生成冲突。

> 风险提示：双主对同一字段高并发写会产生较多冲突，磨合期需限制"同对象同字段双端同时编辑"，靠待办/SLA 引导分工。

### 4.5 同步流程（伪代码）

```
# PLM-2 收到 EBMS 入站
POST /v1/sync/inbound
  -> 幂等校验(event_id)                         # 已存在则忽略
  -> 定位 plm_sync_object(object_type, external_key)
  -> 若不存在: 建映射, 应用(INSERT)
  -> 若存在:
       计算 base = object.revision
       若 payload.revision > base+1 或 updated_at 分叉 -> 记冲突(PENDING), 通知
       否则: 按权威表/字段表应用, revision++, 重新计算 checksum
  -> 写 plm_sync_inbox(APPLIED/CONFLICT)
  -> 发 PLM-2 领域事件（回写确认，避免回环）

# Outbox Job (PLM-2 -> EBMS) 复用 OutboxPublisherJob
  -> 取 status=NEW 事件
  -> 投递 EBMS /api/external-sync/inbound（HMAC 签名, 带 correlation_id）
  -> 成功 PUBLISHED / 失败重试(MAX=5)
```

### 4.6 回环避免

- 事件携带 `correlation_id` 与 `origin_system`；收到自身发起的事件 → 标记已确认不重复应用。
- `plm_sync_object.checksum` 相等 → 跳过。

---

## 五、数据模型迁移映射（逐模块）

> 命名约定：EBMS 对象 → PLM-2 表；字段名以 PLM-2 现有约定（`plm_` 前缀、下划线）落地。状态枚举给出映射。

### 5.1 产品（PRODUCT）

| EBMS | PLM-2 | 映射说明 |
|---|---|---|
| `products.external_model` | `plm_material.part_no` | **外部型号 = 料号（全局主键）**；`internal_model`→`nameEn` 或保留扩展列 |
| `products.product_name/category/power/configuration/specs` | `material_name/part_category/product_type/specification/...` | 品类字典对齐 |
| `products.cost_price/price_rmb/price_usd` | `standard_cost`/`cost_currency` + 扩展价列 | 价字段扩展 |
| 光学/结构字段（color_temp/luminous_flux/...） | `plm_material_param`（参数模板） | 结构化参数承载 |
| `product_bom` | `plm_bom`(EBOM) + `plm_bom_item` | 树形父子 → `parent_item_id`/`path(ltree)` |
| 无 status | 映射 `MaterialStatus` | DRAFT→DRAFT，其余按核价状态映射 |
| 子型号规则 `data_dictionary(sub_model_rule)` | `codegen` 规则 + `plm_code_rule` | 编号规则对齐 |

**缺口**：PLM-2 无"核价记录"概念 → 可映射为参数/或新建 `plm_product_pricing`（待评审）。

### 5.2 研发项目（PROJECT）

| EBMS | PLM-2 | 映射 |
|---|---|---|
| `projects.*` | `plm_project.*` | `project_no/project_name/customer_name/project_type/project_level/urgency/owner/department/start_date/target_date/close_date/current_stage/status/...` 基本同名 |
| `status`: init/executing/completed/paused/cancelled | `status`: ACTIVE/MP/CLOSED/ON_HOLD | init→ACTIVE，executing→ACTIVE，completed→CLOSED，paused→ON_HOLD，cancelled→(新增 CANCELLED 或 CLOSED) |
| `current_stage`（预项目…复盘） | `currentGate`(G0–G8) + `phase` | 需阶段映射表（EBMS 阶段 ↔ G 门） |
| `change_count` | 扩展列/`plm_entity_history` 计数 | 保留 |

### 5.3 进度节点（PROJECT_NODE）——**重点差异**

- EBMS：`rd_project_progress` 单行 22 节点列（plan,bom,spec,config,mold_drawing,mold_review,hand_sample,appearance,structure,electronics,mold,mold_sample,packaging,elec_trial,rd_trial,eng_trial,prod_trial,test_report,tech_transfer,shipment,review,other）+ `node_edits` JSON。
- PLM-2：`plm_project_node` 规范化 **19 行**（PLAN,BOM,SPEC,CONFIG,MOLD_DRAWING,MOLD_REVIEW★,HAND_SAMPLE,MOLD★,MOLD_SAMPLE,PACKAGING,ELEC_TRIAL,RD_TRIAL★,ENG_TRIAL★,PROD_TRIAL★,TEST_REPORT★,TECH_TRANSFER★,SHIPMENT,REVIEW,OTHER）。

**节点映射表**：

| EBMS 节点列 | PLM-2 node_code | 说明 |
|---|---|---|
| plan | PLAN | |
| bom | BOM | |
| spec | SPEC | |
| config | CONFIG | |
| mold_drawing | MOLD_DRAWING | |
| mold_review | MOLD_REVIEW ★ | |
| hand_sample | HAND_SAMPLE | |
| appearance | — | EBMS 独有（外观）→ 合并入 HAND_SAMPLE 或新增节点（待评审） |
| structure | — | EBMS 独有（结构）→ 同上 |
| electronics | — | EBMS 独有（电子）→ 同上 |
| mold | MOLD ★ | |
| mold_sample | MOLD_SAMPLE | |
| packaging | PACKAGING | |
| elec_trial | ELEC_TRIAL | |
| rd_trial | RD_TRIAL ★ | |
| eng_trial | ENG_TRIAL ★ | |
| prod_trial | PROD_TRIAL ★ | |
| test_report | TEST_REPORT ★ | |
| tech_transfer | TECH_TRANSFER ★ | |
| shipment | SHIPMENT | |
| review | REVIEW | |
| other | OTHER | |

**节点值映射**：EBMS 单元格混合值（`V`/`√`=完成、`X`、`进行中`、`待进行`、`暂停`、日期、文字）→ PLM-2 结构化 `status`(NOT_SET/PLANNED/IN_PROGRESS/DONE/FAILED/PENDING) + `planDate` + `actualDate` + `remark`：
- `V`/`√` → DONE（须校验 actual_date + 证据≥1，否则降级 IN_PROGRESS 并记 DQ）
- `X` → FAILED；`进行中` → IN_PROGRESS；`待进行` → PLANNED；`暂停` → PENDING
- 纯日期 → PLANNED + planDate；自由文字 → remark

`node_edits` → `plm_project_node.editCount`；项目 `change_count` ← 节点变更累计。

**结论**：3 个 EBMS 独有节点（appearance/structure/electronics）需决策——**新增为 PLM-2 节点**（推荐，保数据不丢）或并入邻近节点。这是**待评审决策点 D1**。

### 5.4 项目子表

| EBMS | PLM-2 | 映射 |
|---|---|---|
| `rd_project_initiation` | `plm_project_initiation` | 已实现，字段对齐（JSONB 子表已具备） |
| `rd_project_reviews` | 新建 `plm_project_review` | goal_original…action_plan 直接映射 |
| `rd_sales_promotion` | 新建 `plm_project_sales_promotion` | 直接映射 |
| `rd_supply_issues` | 新建 `plm_project_supply_issue` | 直接映射（closed 0/1） |

### 5.5 BOM（BOM / BOM_ITEM / BOM_TYPE / BOM_ISSUE）

| EBMS | PLM-2 | 说明 |
|---|---|---|
| `bom_items`（扁平 + level_depth） | `plm_bom_item`（树 + ltree path） | 层级转换 |
| `product_bom`（单产品树） | `plm_bom`(EBOM) + item | |
| `bom_typed_items` + `bom_types`（产品基础/订单/生产） | 映射 EBOM/MBOM/SBOM 三态 | 订单BOM→MBOM，生产BOM→SBOM（语义近似，需评审） |
| `order_summaries` | 无独立表 → 可映射 `plm_bom`(SBOM) 或新建 | **待评审 D2** |
| `bom_issues`/tracks/preventions | 新建 `plm_bom_issue(_track/_prevention)` | 直接映射，可对接 `plm_issue`（改善闭环） |

### 5.6 样品 / 规格 / 配置（新建）

| EBMS | PLM-2 新建表 | 关键映射 |
|---|---|---|
| `samples` | `plm_sample` | `sample_no/customer_name/inquiry_no/product_code/status/send_date/confirm_date` |
| `spec_sheets` | `plm_spec_sheet` | 光学/电学/包装 30+ 字段直接建列 |
| `config_sheets` | `plm_config_sheet` | 同上 |
| `product_configs` | `plm_product_config` | 结构/电子/包装/证书/特殊 5 组字段 + `bom_details`/`pricing_data` JSONB |

> 与 PLM-2 现有「参数模板 `plm_param_tpl` + `plm_material_param`」的关系：规格/配置表**保留独立结构化表**（对外交付物属性），参数模板用于物料建档；两者通过 `model/part_no` 关联（**待评审 D3**）。

### 5.7 技术转移（TECH，新建，工作量最大）

| EBMS | PLM-2 新建表 | 复用 |
|---|---|---|
| `tech_documents` + `tech_document_versions` | `plm_tech_doc` + `plm_tech_doc_version` | 文件走 `plm_file`（内外网/水印/预览/下载留痕）；两级审批映射 `LifecycleService` |
| `tech_transfer_flows` | `plm_tech_flow` | 四段阶段（presale/rd/production/delivery） |
| `tech_transfer_handovers` | `plm_tech_handover` | 交底单，通过后回写进度节点 |
| `tech_changes` | `plm_tech_change` | 变更审核/执行/校验 |
| `tech_reviews` / `tech_cases` | `plm_tech_review` / `plm_tech_case` | 复盘 → 案例 |
| `tech_access_logs` | 复用 `sys_operation_log` / `plm_file` 日志 | 泄密预警 → 规则 |
| `settings.tech_level_config` | `sys_dict` 或新建配置表 | 分级/水印策略 |

**映射重点**：技转阶段完成 → 回写 `plm_project_node.tech_transfer/review`（与 EBMS 行为一致）。

### 5.8 合规与订单汇总（不迁）

- `compliance_reports` / `compliance_issues`：非研发（M02 数据治理），**不新建表、不迁页面**；如后续有需要，仅映射 `plm_dq_debt` / `plm_issue`，不落独立表（D4）。
- `order_summaries`（订单汇总/毛利）：属财务/销售，**不迁**（D2）。

### 5.9 不迁移清单

- `test_*`（模块 13）：EBMS 开发自测工具，非 M04 业务对象（D7）。
- 销售财经域：询价/核价/客户/订单/报价/物料/采购/供应商等（非研发）。

---

## 六、API 与事件契约

### 6.1 PLM-2 新增/复用 API（对外，供 EBMS 调用）

```
# 同步
POST /api/v1/sync/inbound          # EBMS 变更入站（幂等）
GET  /api/v1/sync/outbound/pending # EBMS 主动拉取待推送变更(复用 /v1/integration/events/pending)
GET  /api/v1/sync/objects/{type}   # 对账：对象映射与版本
GET  /api/v1/sync/conflicts        # 冲突列表
POST /api/v1/sync/conflicts/{id}/resolve  # 人工裁决
POST /api/v1/sync/reconcile        # 触发对账

# M04 领域（EBMS 消费 Result/Exception）
GET  /api/v1/integration/projects/{projectNo}
GET  /api/v1/integration/projects/{projectNo}/nodes
POST /api/v1/integration/parts/lookup
GET  /api/v1/integration/parts/{partNo}/cbom   # 已有
```

### 6.2 事件契约（新增，遵循 Contract Pack V1）

| event_type | object_ref | 触发 |
|---|---|---|
| `m04.product.created/updated/deleted` | PRODUCT | 产品变更 |
| `m04.project.created/updated/status_changed` | PROJECT | 项目变更 |
| `m04.project_node.updated` | PROJECT_NODE | 节点变更 |
| `m04.bom.updated` / `m04.bom_issue.created` | BOM / BOM_ISSUE | BOM 变更/问题 |
| `m04.sample.updated` | SAMPLE | 样品 |
| `m04.spec.updated` / `m04.config.updated` | SPEC / CONFIG | 规格/配置 |
| `m04.tech_doc.updated` / `m04.tech_change.*` | TECH_DOC / TECH_CHANGE | 技转 |

事件字段（AEOS Event Contract）：`event_id, event_type, object_ref{object_type,object_id,external_key}, occurred_at, source_system, correlation_id, version, evidence_refs`。

### 6.3 EBMS 侧最小适配（唯一新增）

- `backend/routes/sync.js`（新）：`POST /api/sync/inbound`（接收 PLM-2 推送）、`POST /api/sync/outbound/push`（主动推）、M04 写操作埋点 → 生成变更事件。
- 不改 EBMS 现有业务逻辑，仅挂钩子（DEBT-M04-002 例外）。

---

## 七、前端迁移方案（原生 Vue3）

EBMS 11 个原生 HTML 页面 → PLM-2 Vue3 视图，复用 PLM-2 现有 `request.js` / `Element Plus` / `DataIOBar` / `OperationGuide`。

| EBMS 页面 | PLM-2 新视图 | 现有可复用 | 估行数 |
|---|---|---|---|
| product.html (999) | `views/product/index.vue`（或并入 material） | material/index + wizard | ~600 |
| project.html (2933) | `views/project/index.vue` 扩展 + `progress.vue` + `sub/*` | project/index,progress,initiation | ~1200 |
| bom.html (624) | `views/bom/index.vue` 扩展 | bom/index,tree | ~400 |
| bom-compare.html (662) | `views/bom/compare.vue` + `issues.vue` | — | ~500 |
| sample.html (295) | `views/sample/index.vue` | — | ~250 |
| tech-transfer.html (813) | `views/tech/*`（docs/flows/changes/reviews/cases） | archive,file,share | ~700 |
| spec-library.html (293) | `views/spec/index.vue` | — | ~250 |
| config.html (806) | `views/config/index.vue` | — | ~600 |
| config-library.html (230) | 并入 spec 视图 Tab | — | ~150 |
| compliance.html (281) | 并入 `views/dq` 或 `views/improve` | dq,improve | ~200 |
| test.html (379) | **不迁** | — | — |

新增：`views/sync/conflicts.vue`（同步冲突裁决，~200 行）。

路由按 PLM-2 `router/index.js` 的 `workflowStage` 分组追加。

---

## 八、对账与监控

| 任务 | 频率 | 内容 | 输出 |
|---|---|---|---|
| 增量对账 | 15 min | 按 `updated_at > checkpoint` 拉两端变更比对 | 差异 → 冲突/自动修复 |
| 全量对账 | 每日 02:30 | 每对象 `checksum` 全比对 | 分叉清单 |
| 延迟监控 | 5 min | `plm_sync_checkpoint.lag_count` | 告警（>N 条/超 T 分钟） |
| 冲突看板 | 实时 | `plm_sync_conflict` PENDING 数 | 前端「同步冲突」页 |

指标（可入 KPI）：`KPI_SYNC_LAG`（同步延迟）、`KPI_SYNC_CONFLICT`（冲突数）、`KPI_SYNC_FAIL`（失败率）。

---

## 九、实施分期与验收标准（AEOS Gate）

> 依 AEOS 「先边界→功能；先闭环→扩展；先 A0/A1→A2/A3；代码完成≠业务完成」。

| 阶段 | 内容 | 验收标准 | 周期(估) |
|---|---|---|---|
| **P0 冻结** | 边界/Mapping/Data Ownership/Owner/契约骨架/技术债登记 | 本文档 + Contract 契约评审通过；每个对象有 Accountable Owner；DEBT-M04-001~004 登记 | 1 周 |
| **P1 同步中枢 + 项目域** | Sync Hub（Inbox/Outbox/映射/冲突/对账）+ correlation_id/幂等 + 研发项目/进度节点/立项/复盘/异常/推广 双向同步 | 两端改项目/节点可见同步；冲突可记录并裁决；对账无遗漏；测试证据 | 4–5 周 |
| **P2 主数据与结构** | 产品/规格/配置/样品/BOM/BOM类型/BOM问题 | 各模块 CRUD+双向同步+导入导出；数据迁移核对一致率 ≥99.5% | 5–6 周 |
| **P3 技转** | 技转（文档/流程/交底/变更/复盘/案例） | 技转审批/水印/下载留痕；阶段回写进度节点 | 5–6 周 |
| **P4 收敛（磨合期后）** | EBMS 研发模块转只读；去双主；契约收紧 | EBMS 研发只读；PLM-2 唯一事实源；关闭 DEBT-001/002 | 2–3 周 |

**每期验收**：单元测试通过 + 端到端实测（两端创建→同步→冲突→裁决→对账）+ 操作指引更新 + Owner Verify + Evidence 归档。
**模板**：`EBMS/docs/templates` 与 `AEOS/docs` 的 Evidence/Gate 规范。

---

## 十、风险与约束

| 风险 | 说明 | 约束/对策 |
|---|---|---|
| 双主分叉 | AEOS 红线，第二事实源 | 对象/字段权威表 + 冲突记录 + 15min 对账 + 限期收敛（DEBT-M04-001） |
| EBMS 无 Outbox | 变更捕获需埋点 | 仅在写路径挂钩子，不改业务（DEBT-M04-002） |
| 编号不一致 | 两套主键（part_no vs external_model） | 统一以料号为 `external_key`，codegen 规则对齐 |
| 节点模型差异 | EBMS 22 节点 vs PLM-2 19 节点 | 决策点 D1，补 3 节点或合并 |
| 文件迁移 | 仅 tech 有文件（`uploads/tech-transfer/`） | 迁移到 `plm_file`（内外网/水印），保留 md5 校验 |
| 数据量大 | `bom_items` 7.9MB、materials 14MB | 分批增量迁移 + 校验和 |
| 性能 | 双写+对账压力 | 增量、限流、游标水位、异步 Job |
| GB18030 编码 | EBMS `project.html` 为 GB18030 | 解析脚本按 GB18030 读取，避免中文乱码 |

---

## 十一、Owner 与责任分工（待指定）

| 域 | Accountable Owner | System Lead |
|---|---|---|
| M04 研发自治中心（总体） | 待指定 | PLM-2 研发负责人 |
| 同步中枢（M11/M02 能力） | 待指定 | 后端架构 |
| 产品/物料主数据 | 待指定 | |
| 项目/进度 | 待指定 | |
| BOM | 待指定 | |
| 技转/规格/配置 | 待指定 | |
| 合规映射 | 待指定 | |
| 前端迁移 | 待指定 | |
| 验收/Evidence | 待指定 | |

> 依 `DATA_OWNERSHIP_MATRIX_V1.md`，未明确 Owner 的对象不得进入正式跨域 Contract。

---

## 十二、决策点（已决议，见「零、评审结论」）

| 编号 | 决策点 | 决议 |
|---|---|---|
| D1 | EBMS 独有节点（外观/结构/电子） | ✅ 新增，共 22 节点 |
| D2 | `order_summaries` 订单汇总 | ❌ 不迁 |
| D3 | 规格书/配置表 与 参数模板 | ✅ 独立表 + `part_no` 关联 |
| D4 | 合规自检 | ❌ 不新建表/不迁页面，仅按需映射 dq/issue |
| D5 | 冲突默认权威 | ✅ 产品/项目/BOM PLM-2 优先；样品/规格/配置过渡期 EBMS 优先 |
| D6 | 磨合期 | ✅ 6 个月 + 退出条件 |
| D7 | `test` 模块 | ❌ 不迁 |

---

## 十三、配套产物（本方案需同步产出）

| 文件 | 说明 | 状态 |
|---|---|---|
| 本文档 `docs/m04-rd-autonomous-migration-sync-plan.md` | 总方案 | 本次产出 |
| `docs/m04-contracts-v1.md` | Object/API/Event/Result/Evidence 契约明细（AEOS Contract 格式） | 待评审后产出 |
| `docs/m04-data-mapping-v1.md` | 逐字段迁移映射表（EBMS→PLM-2） | 待评审后产出 |
| `database/26_m04_sync_hub.sql` | 同步中枢建表 | P1 编码时产出 |
| `database/27_m04_sample_spec_config.sql` | 样品/规格/配置建表 | P2 |
| `database/28_m04_tech_transfer.sql` | 技转建表 | P3 |
| `plm-backend/.../sync/**` | 同步中枢后端 | P1 |
| `plm-frontend/src/views/sync/conflicts.vue` 等 | 前端页面 | 分期 |

---

*本方案依 AEOS V7.2 治理原则编制；评审通过后进入 P1 编码。任何偏离需按 `EBMS_GUARDRAILS_V1.md` 登记技术债、Owner 与到期日。*
