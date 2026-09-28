# 模板工作表 ↔ 项目明细表节点 ↔ 研发项目跟踪 —— 对照与统计方案

> 文档编号: HJ-PLM-NODE-SHEET-MAP-v1.0
> 来源: `项目模版26-9-7_智能表版.xlsx`（40 个工作表）→ PLM-2 研发自治中心
> 数据流: **填写模板工作表 → 统计到「项目明细表」(22 节点) → 汇总到「研发项目跟踪」**

---

## 一、数据流总览

```
┌───────────────┐     ┌──────────────────────┐     ┌───────────────────────┐
│ 模板工作表     │ ──► │ 项目明细表(22节点)     │ ──► │ 研发项目跟踪(跨项目)    │
│ (按节点录像/表单)│     │ plm_project_node      │     │ /v1/rd/tracking        │
│ 01..31 / M_*   │     │ 状态/计划/实际/证据     │     │ 一行一项目 × 22 列      │
└───────────────┘     └──────────────────────┘     └───────────────────────┘
        │                        ▲                          ▲
        │  完成判定规则           │  统计(节点→项目)          │  汇总(项目→全局)
        └────────────────────────┴──────────────────────────┘
```

- **填写**：在「工作表表单」里录入该节点的业务数据（BOM/配置表/送检单/测试报告/模具/技转/复盘…）。
- **统计**：按「节点完成判定规则」把工作表结果折算为该节点在「项目明细表」的 状态/计划日期/实际日期/证据。
- **汇总**：把每项目的 22 节点结果汇总进「研发项目跟踪」的对应列（V/X/进行中/日期/文字）。

---

## 二、模板工作表 → 项目明细表节点 → 系统载体 → 完成判定（主对照表）

| # | 节点(编码) | 对应模板工作表 | PLM-2 载体 | 完成判定(DONE 规则) |
|---|-----------|----------------|-----------|---------------------|
| 1 | 计划表 PLAN | 8甘特图 | 项目节点工作表/plm_process_route | 甘特图有计划且经确认 |
| 2 | 基础BOM (BOM) | 5基础BOM / 24客户BOM / 31成本BOM | `plm_base_bom`(5基础) / `plm_bom` | 基础BOM 齐套/已发布 |
| 3 | 规格书 SPEC | 18规格书登记 / 3方案 | `plm_spec_sheet`(新增) | 规格书 状态=已批准 |
| 4 | 配置表 CONFIG | 4配置表 / 23设计输入清单 | `plm_config_sheet`(新增) | 配置表 已签认 |
| 5 | 模具图纸 MOLD_DRAWING | 13模具管理(图纸) / 附件 / 8甘特图 | `plm_file`(档案) | 二维图纸 已发布 |
| 6 | 开模评审 MOLD_REVIEW ★ | 27评审单(开模评审) / 13模具管理(申请) | `plm_review_sheet`(新增) | 评审结论=通过 |
| 7 | 手样 HAND_SAMPLE | 21样品单 / 27评审单(样机评审) | `plm_sample`(新增) | 样机评审通过 |
| 8 | 外观 APPEARANCE | 3方案(外观) / 27评审单(外观评审) | `plm_review_sheet`(新增) | 外观评审通过 |
| 9 | 结构 STRUCTURE | 3方案(结构方案) / 27评审单(结构评审) | `plm_review_sheet`(新增) | 结构评审通过 |
| 10 | 电子 ELECTRONICS | 3方案(电子方案) / 10异常 | `plm_review_sheet`(新增) | 电子样品确认 |
| 11 | 模具 MOLD ★ | 13模具管理 | `plm_mold` + `plm_mold_trial_log` | 模具验收 / T0 完成 |
| 12 | 模样 MOLD_SAMPLE | 21样品单(模具样品) / 27评审单(样品评审) | `plm_sample` | 样品确认单签署 |
| 13 | 包装设计 PACKAGING | 附件:包装平面图纸 | `plm_file` | 包装确认 |
| 14 | 电试 ELEC_TRIAL | 26测试报告(电) / 29试产报告(电子试产) | `plm_test_report`(新增) | 报告结论=合格 |
| 15 | 研试 RD_TRIAL ★ | 29试产报告(研发试产) | `plm_trial_report`(新增) | 报告结论=通过 |
| 16 | 技转 TECH_TRANSFER ★ | 28技转报告 | `plm_tech_*`(新增) | 技转报告 状态=完成 |
| 17 | 工试 ENG_TRIAL ★ | 29试产报告(工程试产) | `plm_trial_report`(新增) | 报告结论=通过 |
| 18 | 生试 PROD_TRIAL ★ | 29试产报告(生产试产) | `plm_trial_report`(新增) | 报告结论=通过 |
| 19 | 测试报告 TEST_REPORT ★ | 25送检单 / 26测试报告 | `plm_test_report`(新增) | 判定=合格 |
| 20 | 出货 SHIPMENT | 22订单登记 / 30验货对比报告 | `plm_shipment`(新增) 或订单登记 | 客户签收 / 验货合格 |
| 21 | 复盘 REVIEW | 11复盘 | `plm_project_review`(已有) | 复盘完成并归档 |
| 22 | 其他 OTHER | 10异常 / 16技术货架引用 / 19培训记录 / 20内审与管理评审 | 各自模块 | 自定义 |

★ = 关键节点(7 个)：开模评审 / 模具 / 研试 / 工试 / 生试 / 测试报告 / 技转。

---

## 三、跨节点/跨项目的公共工作表（不单独成节点）

| 模板工作表 | 归属 | PLM-2 载体 | 作用 |
|-----------|------|-----------|------|
| 项目主表 | 研发项目数据库 | `plm_project` | 生成/维护项目（汇总表的一行） |
| 6立项申请 | 立项申请 | `plm_project_initiation` | 批准后转项目（G0） |
| 9证据清单 | 证据台账 | `plm_project_node_evidence` | 各节点证据齐套 |
| 10异常 | 供应链品质异常 | `plm_project_supply_issue` | 异常闭环 |
| 17工程变更管理 | ECN | `plm_ecn` | 变更影响各节点 |
| 12到料生产计划表 | 交付域(M06) | 预留/外部 | 到料/生产进度 |
| 14检验记录 | 品质检验 | `plm_inspection_standard` | IQC/IPQC |
| 15采购与供应商管理 | 交付域(M06) | 预留/外部 | 采购/供应商 |
| 1项目流程 / 2表单标准 | 流程与标准 | 字典/标准作业 | 定义 |
| M_客户/产品/物料/供应商/人员/部门 | 主数据 | `plm_material`/`sys_*` 等 | 主数据维表 |
| 数据字典 | 数据治理 | `sys_dict` | 枚举/字段说明 |
| 0首页看板 | 看板 | `dashboard` | 统计 |
| 0智能表设计说明 | 说明 | — | 说明 |

---

## 四、统计规则

1. **工作表 → 节点**：每张（或每组）工作表汇总为该项目的**一个节点记录** `plm_project_node`：
   - `status`：由完成判定规则决定（DONE/IN_PROGRESS/…）
   - `plan_date`：工作表计划日期（如甘特图/送检要求完成日期）
   - `actual_date`：工作表完成/批准日期
   - `evidence_count`：工作表证据/附件数（9证据清单/附件）
2. **项目 → 项目明细表**：单项目的 22 行节点即「项目明细表」。
3. **汇总 → 研发项目跟踪**：跨项目按列聚合，单元格值沿用 EBMS 语义：
   - DONE→`V`；FAILED→`X`；IN_PROGRESS→`进行中`；PENDING→`待定`；PLANNED→计划日期；空→`—`。
4. **完成硬标准（关键节点）**：`status=DONE` 需 `actual_date` + 证据≥1（见 `plm-project-progress-tracking-spec.md`）。

---

## 五、新增载体表清单（落地建议）

| 表 | 对应工作表 | 关键字段 |
|----|-----------|---------|
| `plm_spec_sheet` | 18规格书登记/3方案 | model, version, status, 光学/电学参数, approved_by/at |
| `plm_config_sheet` | 4配置表/23设计输入清单 | model, 结构/电子/包装/证书/特殊, status |
| `plm_sample` | 21样品单 | sample_no, project_no, 样品名称/规格/数量/用途/状态 |
| `plm_review_sheet` | 27评审单 | review_no, project_no, 评审类型(开模/结构/外观/样品), 结论, 整改 |
| `plm_test_report` | 25送检单/26测试报告 | report_no, project_no, 测试类型/项目/标准/实测/判定 |
| `plm_trial_report` | 29试产报告 | report_no, project_no, 试产批次/数量/良品/不良率/结论 |
| `plm_shipment` | 22订单登记/30验货对比报告 | order_no, project_no, 验货/判定/完成 |
| `plm_node_sheet_map` | 本对照关系 | node_code, worksheet_keys, source_tables, done_rule |

> 说明：BOM/模具/ECN/品质/复盘/立项 已存在于 PLM-2，直接复用；其余按上表新增。

---

## 六、实现分期

| 期 | 内容 |
|----|------|
| P1 | 建 `plm_node_sheet_map` 映射表 + 接口；跟踪页展示「节点↔工作表」对照 |
| P2 | 新增规格/配置/样品/评审/测试/试产/出货 工作表（CRUD + 导入导出） |
| P3 | 完成判定与统计服务：工作表 → 节点状态；工作表 → 项目明细表 |
| P4 | 汇总到研发项目跟踪（列值实时/定时刷新）+ 看板 |

---

## 七、系统内「节点 ↔ 工作表」一一对应（已在系统落地）

> 入口：**项目明细表**（研发自治中心 → 项目明细表）每个节点行 →「填工作表」按钮，按本表打开对应工作表并带入项目号/类型。

| 节点(编码) | 打开的工作表 | 带入类型 | 状态 |
|-----------|-------------|---------|------|
| 计划表 PLAN | 计划表(甘特) `plan` | — | ✅ 新增 |
| 基础BOM BOM | 基础BOM(5) `basebom` | — | ✅ 新增(原 BOM 改名) |
| 规格书 SPEC | 规格书 `spec` | — | ✅ |
| 配置表 CONFIG | 配置表 `config` | — | ✅ |
| 开模评审 MOLD_REVIEW | 评审单 `review` | 开模评审 | ✅ |
| 外观 APPEARANCE | 评审单 `review` | 外观评审 | ✅ |
| 结构 STRUCTURE | 评审单 `review` | 结构评审 | ✅ |
| 电子 ELECTRONICS | 评审单 `review` | 电子评审 | ✅ |
| 手样 HAND_SAMPLE | 评审单 `review` | 样机评审 | ✅ |
| 模样 MOLD_SAMPLE | 评审单 `review` | 样品评审 | ✅ |
| 技转 TECH_TRANSFER | 评审单 `review` | 技转评审 | ✅ |
| 电试 ELEC_TRIAL | 试产报告 `trial` | 电子试产 | ✅ |
| 研试 RD_TRIAL | 试产报告 `trial` | 研发试产 | ✅ |
| 工试 ENG_TRIAL | 试产报告 `trial` | 工程试产 | ✅ |
| 生试 PROD_TRIAL | 试产报告 `trial` | 生产试产 | ✅ |
| 测试报告 TEST_REPORT | 送检/测试报告 `test` | — | ✅ |
| 出货 SHIPMENT | 出货/验货 `shipment` | — | ✅ |

工作表入口：**项目明细表**节点「填工作表」→ `项目工作表`页（`/project/worksheets`）。

---

## 八、对不上清单（需你确认/后续处理）

### 8.1 无工作表、且不属"新增工作表"范畴的节点（6 个）

| 节点 | 模板来源 | 现状 | 建议 |
|------|---------|------|------|
| MOLD 模具 | 13模具管理 | PLM-2 已有 **模具模块** `plm_mold` | 从节点跳模具模块 |
| MOLD_DRAWING 模具图纸 | 13模具管理(图纸)/附件 | **未建工作表**(图纸/档案) | 走档案/附件模块 |
| PACKAGING 包装设计 | 附件:包装平面图纸 | **未建工作表** | 走档案/附件模块 |
| REVIEW 复盘 | 11复盘 | PLM-2 已有 **项目复盘** `plm_project_review` | 从节点跳复盘页 |
| OTHER 其他 | 10异常/16技术货架/19培训/20内审 | **未建**(多来源) | 保持自定义 |

### 8.2 非严格一一对应（多节点共用一张工作表，用"类型"区分）

> 严格一一对应应是"一节点一表"。以下为**多对一**，需确认是否接受：

| 工作表 | 承载节点 | 区分字段 |
|--------|---------|---------|
| 评审单 `review` | 开模评审 / 外观 / 结构 / 电子 / 手样(样机) / 模样(样品) / 技转（共 7 个） | `review_type` |
| 试产报告 `trial` | 电试 / 研试 / 工试 / 生试（共 4 个） | `trial_type` |
| 送检/测试报告 `test` | 测试报告（并可为电试提供证据） | `test_type` |

建议：接受"多对一 + 类型字段"（同一张表按类型归档不同节点），无需拆表。

### 8.3 模板中"无对应节点"的工作表（3 个，作为公共/基础）

| 模板工作表 | 落点 |
|-----------|------|
| 项目主表 / 6立项申请 | 研发项目数据库 / 立项申请 |
| 9证据清单 | 证据台账 |
| 10异常 / 17工程变更 / 12到料生产 / 14检验 / 15采购 | 异常 / ECN / 交付域 / 品质 / 采购 |

### 8.4 基础表（M_*）→ 系统管理模块

| 模板 | 系统落点 | 状态 |
|------|---------|------|
| M_客户 | 系统管理 → 基础数据 → M_客户 | ✅ 新增 |
| M_产品 | 系统管理 → 基础数据 → M_产品 | ✅ 新增 |
| M_物料 | 系统管理 → 基础数据 → M_物料 | ✅ 新增 |
| M_供应商 | 系统管理 → 基础数据 → M_供应商 | ✅ 新增 |
| M_人员 | 系统管理 → 基础数据 → M_人员 | ✅ 新增 |
| M_部门 | 系统管理 → 基础数据 → M_部门 | ✅ 新增 |
| 数据字典 | 系统管理 → 字典管理 | 已有 |

> 入口：系统管理 → 基础数据（`/admin/base`）。数据表 `m_*`，通用 CRUD + 导入/导出。

---

## 九、遵循 AEOS 标准（工作表自动引用明细表所选项目）

依据 `AEOS` 仓库：`contracts/DEH_CONTRACT_V1.md`（Common Envelope / Object / Event / Evidence 契约）、`architecture/DATA_OWNERSHIP_MATRIX_V1.md`（M04 归属）、`ledger/DEVELOPMENT_LEDGER_V1.md`（G0-G6 门禁）。

**① 自动引用（Object Contract：引用不复制）**
- 从「项目明细表」节点打开工作表时，带入所选项目并**只读引用**其主数据（项目编号/名称/客户/负责人/产品型号）。
- 接口：`GET /v1/rd/sheets/context?projectNo=` → 返回
  `{ tenant_id, source_system, objectRef:{object_type=PROJECT, object_id, external_key=project_no}, fields:{...} }`。
- 新建工作表记录时自动写入 `project_id`（引用所选项目 object_id）+ `project_no`（external_key），**不复制**客户/产品主数据。

**② Common Envelope（DEH §1）**
工作表记录统一携带：`tenant_id(=hjgd)`、`source_system(=PLM2)`、`revision`、`created_by`、`correlation_id`（+ 既有 `created_at/updated_at`）。

**③ Event Contract（DEH §7 / 集成规则）**
工作表新增/修改发出领域事件 `m04.worksheet.created` / `m04.worksheet.updated`，payload 含 `object_ref{object_type,object_id,external_key}`、`worksheet_type`、`correlation_id`、`action`（经 `plm_domain_event` Outbox）。

**④ Evidence / Audit**
工作表变更同步写入「变更列表」`plm_project_change_log`（时间/对象/动作/来源/操作人）。

**⑤ 边界（System Registry / Integration Rules）**
工作表与项目同属 **M04 研发自治中心**，为域内引用；跨域一律走 Contract/Event，禁止跨域直写。

> 对应门禁：G0（契约/Object Ref）与 G3（Fact→Event→…→Evidence 可追溯）在本域内已满足。

---

*本对照表为模板工作表与 PLM-2 研发自治中心的唯一映射依据；调整须更新版本。*
