# M04 研发自治中心 · 逐字段迁移映射 v1

> 文档编号: HJ-PLM-M04-MAPPING-v1.0
> 来源: EBMS（SQLite/JSON，表名即历史 JSON 文件名）→ 目标: PLM-2（PostgreSQL）
> 关联: `docs/m04-rd-autonomous-migration-sync-plan.md`、`docs/m04-contracts-v1.md`
> 约定: 目标表统一 `plm_` 前缀；新增列含同步字段 `source_system / revision / sync_status / correlation_id`（简记为 **[同步列]**）。

---

## 0. 通用规则

1. **主键对齐**：EBMS 产品 `external_model` ↔ PLM-2 `plm_material.part_no`（全局唯一料号）；项目 `project_no`；明细用业务键。
2. **同步列**：所有迁入表追加 `source_system VARCHAR(16)`、`revision INT DEFAULT 0`、`sync_status VARCHAR(16) DEFAULT 'SYNCED'`、`correlation_id UUID`。
3. **状态枚举**：见各模块；无法直接映射的按「映射表」转换，未知值落 `ext_json`。
4. **时间**：EBMS `created_at/updated_at`（字符串 `YYYY-MM-DD HH:mm:ss`）→ `TIMESTAMPTZ`。
5. **附件**：仅技转有文件；迁移到 `plm_file`（内外网/水印），校验 `md5`。

---

## 1. 产品 PRODUCT

### 1.1 `products` → `plm_material`

| EBMS 字段 | PLM-2 字段 | 转换 |
|---|---|---|
| `id` | （映射表存 source_id） | 经 `plm_sync_object` |
| `external_model` | `part_no` | 外部型号 = 料号 |
| `internal_model` | `name_en`（或扩展列 `internal_model`） | |
| `product_name` | `material_name` | |
| `category` | `product_type` / `part_category` | 字典对齐（`sys_dict product_category`） |
| `power` | `power_w`（数值化） + `plm_material_param` | 如 "100W"→100 |
| `configuration` | `specification` | |
| `specs` | `plm_material_param`（JSON→参数项） | |
| `input_voltage/battery/color_temp/luminous_flux/light_source/main_body/press_frame/lampshade/reflector/cable/switch_type/usb/waterproof/sensor` | `plm_material_param`（按参数模板） | 光学/结构参数 |
| `cost_price` | `standard_cost` | |
| `price_rmb/price_usd` | 扩展列 `price_rmb/price_usd` | 商务价（可选保留） |
| `created_at/updated_at` | `created_at/updated_at` | |
| — | `status` | 新增默认 `DRAFT`；`lifecycle_status` 依 AEOS 加 `source_system` |

**状态**：EBMS 无状态 → PLM-2 `MaterialStatus`（DRAFT/REVIEWING/RELEASED/IN_PRODUCTION/CHANGING/OBSOLETE/SEALED），迁移默认 `DRAFT`，由 DQ/核价状态推断。

### 1.2 `product_bom` → `plm_bom`(EBOM) + `plm_bom_item`

| EBMS | PLM-2 | 转换 |
|---|---|---|
| — | `plm_bom.bom_no` | 生成 `BOM-{part_no}-EBOM-V1` |
| — | `plm_bom.root_part_no` | = 产品 `part_no` |
| — | `plm_bom.bom_type` | `EBOM` |
| `parent_id` | `plm_bom_item.parent_item_id` | 树形父子 |
| `level` | `level_no` + `path`(ltree) | 需重建 ltree 路径 |
| `code` | `part_no` | 子件料号 |
| `name/spec/unit/quantity/material_type/material_category/processing_fee/unit_price/amount/is_fee_row/remarks/sort` | 同名列（映射/扩展） | |

---

## 2. 研发项目 PROJECT

### 2.1 `projects` → `plm_project`

| EBMS 字段 | PLM-2 字段 | 转换 |
|---|---|---|
| `project_no` | `project_no` | 原样 |
| `project_name` | `project_name` | |
| `customer_name` | `customer_name`（+`customer_code` 可空） | |
| `project_type` | `project_type` | 客制/自研 → `SELF`/`NEW` 映射（保留原文 ext_json） |
| `project_level` | `project_level` | A/B/C |
| `urgency` | `urgency` | |
| `owner` | `owner` | |
| `department` | `department` | |
| `start_date/target_date/close_date/market_date` | 同名 | 日期 |
| `current_stage` | `current_gate` + `phase` | 阶段↔G 门映射（见 2.2） |
| `node_time` | ext_json | |
| `progress_note/remarks` | `remarks` | 合并 |
| `project_amount/order_amount/invest_amount/annual_order` | 同名 | |
| `gantt_link/doc_link` | ext_json | |
| `change_count` | ext_json（或新建计数列） | |
| `status` | `status` | 映射见下表 |
| `audit_status` | ext_json | |

**状态映射**：`init→ACTIVE`，`executing→ACTIVE`，`completed→CLOSED`，`paused→ON_HOLD`，`cancelled→CANCELLED`（ext_json 存原值）。

**阶段映射（EBMS current_stage ↔ PLM-2 gate）**：

| EBMS 阶段 | PLM-2 gate |
|---|---|
| 预项目 | G0 |
| 方案 | G1 |
| 手样 | G2 |
| 模具开发 | G3 |
| 功能样/首次封样/最终封样 | G4 |
| 电试/研试/工试/生试 | G5 |
| 技转 | G6 |
| 出货 | G7 |
| 复盘/完成 | G8 |
| 暂停 | （保持当前 gate） |

### 2.2 `rd_project_progress` → `plm_project_node`（**22 节点**）

单行 22 列 → 22 行规范化（D1 决议：补 3 节点）。

| EBMS 列 | node_code | seq | is_key |
|---|---|---|---|
| plan | PLAN | 1 | |
| bom | BOM | 2 | |
| spec | SPEC | 3 | |
| config | CONFIG | 4 | |
| mold_drawing | MOLD_DRAWING | 5 | |
| mold_review | MOLD_REVIEW | 6 | ★ |
| hand_sample | HAND_SAMPLE | 7 | |
| **appearance** | **APPEARANCE** | 8 | |
| **structure** | **STRUCTURE** | 9 | |
| **electronics** | **ELECTRONICS** | 10 | |
| mold | MOLD | 11 | ★ |
| mold_sample | MOLD_SAMPLE | 12 | |
| packaging | PACKAGING | 13 | |
| elec_trial | ELEC_TRIAL | 14 | |
| rd_trial | RD_TRIAL | 15 | ★ |
| eng_trial | ENG_TRIAL | 16 | ★ |
| prod_trial | PROD_TRIAL | 17 | ★ |
| test_report | TEST_REPORT | 18 | ★ |
| tech_transfer | TECH_TRANSFER | 19 | ★ |
| shipment | SHIPMENT | 20 | |
| review | REVIEW | 21 | |
| other | OTHER | 22 | |

**单元格值 → 结构化字段**：

| EBMS 值 | status | 其他 |
|---|---|---|
| `V` / `√` | DONE | 需校验 `actual_date` + 证据≥1，否则降 IN_PROGRESS 并记 DQ |
| `X` | FAILED | |
| `进行中`/`T1`/`T2` | IN_PROGRESS | |
| `待进行`/`待定` | PLANNED / PENDING | |
| `暂停` | PENDING | |
| 日期 | PLANNED | `plan_date` |
| 其他文字 | — | `remark` |
| 空 / `/` / `-` | NOT_SET | |

| EBMS | PLM-2 |
|---|---|
| `node_edits`(JSON) | 各节点 `edit_count` |
| `project_name` | `plm_project_node` 无 → 从项目取 |

节点外部键：`{project_no}:{node_code}`。

### 2.3 `rd_project_initiation` → `plm_project_initiation`

已实现，字段对齐：`project_no/project_name/project_type/start_date/department/owner/cooperators`；客户信息组；JSON 子表 `product_specs→productSpecs / feasibility→feasibility / approval_signs→approvalSigns / sales_forecast→salesForecast / special_reqs→specialReqs`；审批 `approval_status/workflow_stage/step1..5_*` 原样。

### 2.4 `rd_project_reviews` → 新建 `plm_project_review`

`project_id/project_no/project_name + goal_original/goal_milestone/result_highlights/result_lowlights/result_actual/success_factors/failure_causes/insights/experience/action_plan/remarks` 直接映射。

### 2.5 `rd_supply_issues` → 新建 `plm_project_supply_issue`

`occur_date/proposer/product_name/order_no/project_no/problem_desc/temp_measure/cause_analysis/long_term_measure/long_term_date/responsible_person/responsible_dept/plan_complete_date/audit/closed(0/1)/remarks` 直接映射。

### 2.6 `rd_sales_promotion` → 新建 `plm_project_sales_promotion`

`project_no/product_model/salesperson/customer/appearance/price/performance/function_feedback/progress/remarks` 直接映射。

---

## 3. 结构/BOM

### 3.1 `bom_items` → `plm_bom_item`

| EBMS | PLM-2 | 转换 |
|---|---|---|
| `product_code` | 父件 `root_part_no` | |
| `level`(".1.1") / `level_depth` | `level_no` / `path`(ltree) | 层级转换重建 |
| `material_code` | `part_no` | |
| `material_name/spec/aux_attr/unit/quantity` | 同名 | |
| `material_attr`(自制/委外/外购) | `make_type` | 枚举映射 |
| `key_part` | 扩展列 `key_part` | |
| `is_disabled` | `status`（禁用→OBSOLETE? 保留扩展列） | |
| 成本列（direct_material/direct_labor/...） | `plm_bom_item` 扩展成本列 | |
| `source` | ext_json | |

### 3.2 `bom_types` + `bom_typed_items` → `plm_bom` 三态

| EBMS bom_type | PLM-2 `bom_type` |
|---|---|
| product_base（产品基础BOM） | `EBOM` |
| order（订单BOM） | `MBOM` |
| production（生产BOM） | `SBOM` |

`bom_typed_items`：`product_id/base_bom_id/order_id/modified` → `source`/`ecn_no` 等扩展列；`order_id` 非研发 → 落 ext_json。

### 3.3 `bom_issues`/tracks/preventions → 新建 `plm_bom_issue(_track/_prevention)`

`title/description/category/severity/product_id/product_model/code/field/old_value/new_value/assignee/due_date/source/status/resolution/resolved_at` 直接映射。
状态：`open→OPEN, in_progress→IN_PROGRESS, resolved→RESOLVED, closed→CLOSED, rejected→REJECTED`。
可对接 `plm_issue`（改善闭环，source_type=BOM_ISSUE）。

---

## 4. 样品 SAMPLE

### `samples` → 新建 `plm_sample`

`sample_no/customer_name/inquiry_no/product_name/product_code/quantity/sample_type/status/remarks/send_date/confirm_date` 直接映射（`inquiry_no` 非研发但作为来源引用保留）。
状态：`pending→PENDING, confirmed→CONFIRMED, producing→PRODUCING, sent→SENT, customer_confirmed→CUSTOMER_CONFIRMED, completed→COMPLETED`。

---

## 5. 规格书/配置表

### 5.1 `spec_sheets` → 新建 `plm_spec_sheet`

30+ 字段直接映射：`inquiry_id/description/model_no/version/file_no + 光学/电学/包装/结构参数 + status + created_at/updated_at`。
状态：`draft→DRAFT`；其余按写入值保留。

### 5.2 `config_sheets` → 新建 `plm_config_sheet`

字段直接映射（结构/电子/包装/证书/特殊 + `inquiry_id/model/status`）。

### 5.3 `product_configs` → 新建 `plm_product_config`

5 组字段直接建列；`bom_details`/`pricing_data` → JSONB；`inquiry_id/model/status`。
状态：`draft→DRAFT, imported→IMPORTED, confirmed→CONFIRMED`。

> D3：与 `plm_param_tpl`/`plm_material_param` 并存，以 `model ↔ part_no` 关联。

---

## 6. 技术转移 TECH（新建）

### 6.1 `tech_documents` + `tech_document_versions` → `plm_tech_doc` + `plm_tech_doc_version`

| EBMS | PLM-2 | 转换 |
|---|---|---|
| `doc_no/title/category/sub_category/level/stage` | 同名 | `level` 1/2/3 保留 |
| `project_id/project_no/inquiry_no/sample_id/customer_name/owner_dept` | 同名 | |
| `file_path/file_name/file_ext/file_size` | `plm_file` 引用（`file_id`） | 迁移文件到 `files/intranet` |
| `file_hash` | `plm_file.md5_hash` | 校验 |
| `file_signed_path` | `plm_file`（签名版单独记录） | |
| `version/prev_version` | `version_no` | |
| `status/approval_stage/dept_*/gm_*` | 审批字段 | 映射 `LifecycleService` / 独立列 |
| `watermark/allow_preview/allow_download/allow_forward` | 扩展列 | |
| `uploaded_by/archived_at/description` | 同名 | |

状态：`新增→DRAFT, 待审核→PENDING, 已归档→ARCHIVED, 已作废→OBSOLETE, 已迭代→ITERATED`。
审批：`dept_review→PENDING_L1, gm_approve→PENDING_L2, approved→APPROVED, rejected→REJECTED`。

`tech_document_versions`：`doc_id/version/file_*/change_summary/change_reason/changed_by/is_current` → `plm_tech_doc_version`（file 走 `plm_file`）。

### 6.2 `tech_transfer_flows` → `plm_tech_flow`

`project_id/project_no/inquiry_no/customer_name + presale_status/rd_status/production_status/delivery_status + current_stage/tech_lead/handover_count/change_count` 直接映射。
阶段：`presale→PRESALE, rd→RD, production→PRODUCTION, delivery→DELIVERY`；阶段状态 `未开始→NOT_STARTED, 进行中→IN_PROGRESS, 完成→DONE`。

### 6.3 `tech_transfer_handovers` → `plm_tech_handover`

`handover_no/flow_id/project_id/project_no/inquiry_no/customer_name/stage/doc_ids[]/from_user/from_role/to_user/to_role/content/status/audited_*` 直接映射（`doc_ids` → JSONB 或关联表）。
**联动**：审核通过 → 推进 flow 阶段；`rd` 完成 → 回写 `plm_project_node.tech_transfer=DONE`；`delivery` 完成 → `review=DONE`。

### 6.4 `tech_changes` → `plm_tech_change`

`change_no/project_id/project_no/change_type/scope/before_value/after_value/reason/impact_analysis/status/initiator/auditor/related_doc_ids[]/notify_roles/executed_at/verified_at/verified_note` 直接映射。
状态：`发起→INITIATED, 审核中→IN_REVIEW, 已通过→APPROVED, 已驳回→REJECTED, 已执行→EXECUTED, 已校验→VERIFIED`。

### 6.5 `tech_reviews` / `tech_cases` → `plm_tech_review` / `plm_tech_case`

`tech_reviews`：`review_no/project_id/project_no/customer_name/dimension/findings/issues/improvements/action_plan/risk_level/owner/status` → `plm_tech_review`。
`tech_cases`：`case_no/title/product_category/problem_type/problem_desc/root_cause/solution/source_project_id/source_project_no/related_review_id/tags/view_count/created_by` → `plm_tech_case`。

### 6.6 `tech_access_logs` → `plm_file_access_log` 或 `sys_operation_log`

`doc_id/doc_title/user_id/user_name/action/result/reason/ip/user_agent/created_at` → 复用日志表 + 泄密预警规则。

---

## 7. 编号规则对齐

| 对象 | EBMS | PLM-2 `seq_key` |
|---|---|---|
| 产品 | `external_model` 手填 | `PART_NO` |
| 项目 | `project_no` | `PROJECT_NO` |
| 立项 | — | `INITIATION_NO` |
| BOM | 生成 | `BOM_NO` |
| 技转资料 | `JZ+日期-序列` | `TECH_DOC_NO`（新增） |
| 交底单 | `HS...` | `TECH_HANDOVER_NO`（新增） |
| 技术变更 | `BG...` | `TECH_CHANGE_NO`（新增） |
| 案例 | `AL...` | `TECH_CASE_NO`（新增） |
| 样品 | `SMP+时间` | `SAMPLE_NO`（新增） |

---

## 8. 迁移与校验

1. 一次性导入：`dataio` 引擎/定制迁移脚本，按 `external_key` upsert，写 `plm_sync_object`。
2. 校验：条目数一致 + 关键字段 checksum + 附件 md5。
3. 差异报告：不一致条目落 `plm_sync_conflict` 人工确认。
4. 迁移后开启双向同步（P1 Sync Hub）。

---

*本映射为 P0 冻结版；编码前如需调整须更新版本号。*
