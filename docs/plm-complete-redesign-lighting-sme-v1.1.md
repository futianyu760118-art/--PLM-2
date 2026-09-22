# 工贸一体灯具 PLM 系统设计说明书 V1.1

> **文档编号**: HJ-PLM-SME-V1.1-20260722  
> **继承**: V1.0（`plm-complete-redesign-lighting-sme.md`）全部业务范围与边界  
> **本版增量**: **自动化 · 精益化 · 编码自动生成 · 数据自检 · 智能分析 · 问题改善闭环 · KPI/OKR 数据底座 · 智能体体系**  
> **适用**: 100–300 人 · 营收 0.5–2 亿 · 灯具工贸一体

---

## 0. V1.1 一句话定义

> **在 V1.0「工程真相源」之上，建成「少人干预的精益 PLM」：能自动编号、自动检错、自动度量、自动分析、自动提改善，并由智能体辅助执行；一切行为与指标落库，可对接企业 KPI/OKR。**

### 0.1 四大运营原则（全模块强制）

| 原则 | 含义 | 系统落点 |
|------|------|----------|
| **自动化 Automation** | 能机器做的不进人；人只做决策与例外 | 编号器、状态副作用、事件总线、夜间批检、智能体工具调用 |
| **精益 Lean** | 消除等待、返工、多余搬运（信息流） | 门禁前置、一次做对、待办拉动、WIP 限制、标准作业 |
| **自检 Self-Check** | 数据自己证明自己对 | 规则引擎 + 质量得分 + 阻断/预警 |
| **闭环 PDCA** | 发现→分析→对策→验证→标准化 | 问题单 + 改善单 + KPI 回写 + 案例库 |

### 0.2 精益在 PLM 中的「七大浪费」对照

| 浪费 | 灯具 PLM 场景 | 系统对策 |
|------|----------------|----------|
| 过度加工 | 重复填属性、重复建文件夹 | 模板+自动档案树+参数继承 |
| 等待 | 等编号、等审、等齐套 | 自动编号、并行门禁、超时升级 |
| 多余搬运 | 多系统拷贝料号/BOM | 单真相 + 集成推送 |
| 库存 | 草稿/僵尸料号堆积 | WIP 限额、超期清理任务 |
| 动作浪费 | 到处找图纸版本 | 档案树+版本时间线+where-used |
| 缺陷 | 错 BOM、缺图发布 | 自检阻断发布 |
| 人才浪费 | 工程师当数据录入员 | 智能体填单/检查/起草 ECN |

---

## 1. 目标架构 V1.1（在 V1.0 上叠加智能层）

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 门户：工作台(待办拉动) │ 精益看板 │ 分析驾驶舱 │ 智能体对话 │ 业务模块…    │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────────────┐
│                     Agent Orchestrator 智能体编排层                       │
│  编码助手 │ 质检官 │ 分析师 │ 改善教练 │ 变更助理 │ 技转检查员 │ 问答员   │
│  (Tool Calling → 领域 API；全过程写入 agent_* 表)                        │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────────────┐
│  PLM Core 业务域（V1.0） + 精益自动化横切能力                              │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────────────────┐  │
│  │自动编号  │自检引擎  │度量采集  │分析服务  │改善闭环              │  │
│  │CodeGen   │DataQuality│Metric   │Analytics │Issue/A3/Action       │  │
│  ├──────────┴──────────┴──────────┴──────────┴──────────────────────┤  │
│  │ KPI/OKR 底座 │ 事件Outbox │ 调度Job │ 通知升级 │ 标准作业SOP库    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  Part · Project · BOM · ECN · Doc · Mold · Process · Trade · Integration │
└───────────┬─────────────────────────────┬───────────────────────────────┘
            │                             │
     Algorithm/LLM                 PostgreSQL + Redis
     (3D + 本地/云大模型)          业务库 + 度量库 + 向量/全文(可选)
```

**部署仍保持模块化单体**（适配 100–300 人 IT 产能）；智能体是**进程内编排 + 异步 Worker**，不是一堆独立微服务。

---

## 2. 自动编码体系（CodeGen）

### 2.1 设计目标

- **零手工编料号**（允许在规则内预览后一键确认；特批号走权限）。  
- 所有业务单据号可追溯生成上下文。  
- 规则可配置、可版本化、可回放。

### 2.2 编号对象（全覆盖）

| 对象 | 默认规则示例 | seq_key |
|------|--------------|---------|
| 成品 Part | `HJ{品类2}{yyyy}{seq4}` | PART_FG |
| 结构件 | `2.{大类}.{中类}.{seq5}` | PART_STR |
| 电子件 | `3.{…}` | PART_ELC |
| 包材 | `4.{…}` | PART_PKG |
| 项目 | `HJ.{yy}{seq3}.{客户简码}` | PROJECT |
| ECR/ECN | `ECR/ECN-yyyyMMdd-seq3` | ECR/ECN |
| BOM | `BOM-{partNo}-{type}-{ver}` | BOM |
| 模具 | `M-{partNo}-{cavity}` | MOLD |
| 外协单 | `OS-yyyyMMdd-seq3` | OUTSRC |
| 文件 | `{partNo}_{docType}_V{ver}` | FILE |
| 问题单 | `IQ-yyyyMMdd-seq4` | ISSUE |
| 改善单 | `CI-yyyyMMdd-seq4` | IMPROVE |
| 智能体会话 | `AG-…` | AGENT |
| 度量批次 | `MB-…` | METRIC_BATCH |

### 2.3 数据模型

```sql
-- 编号规则头（可版本）
plm_code_rule (
  id, rule_code, object_type, name, version_no, status, -- DRAFT/ACTIVE/OBSOLETE
  pattern,          -- 如: HJ{cat}{yyyy}{seq:4}
  seq_key, reset_policy, -- NEVER/YEAR/MONTH/DAY
  uniqueness_scope, -- GLOBAL / CATEGORY
  preview_enabled, allow_manual_override, override_roles,
  effective_from, effective_to, created_at, updated_at
)

plm_code_rule_segment (
  id, rule_id, seq_no, segment_type, -- CONST/DATE/DICT/SEQ/FIELD/HASH
  value_expr, pad_length, dict_type, field_path, transform
)

-- 继承并增强 sys_sequence
sys_sequence (seq_key, prefix, date_pattern, length, current_val, last_reset_at, ...)

plm_code_issue_log (
  id, object_type, object_id, generated_code, rule_id, rule_version,
  context_json, issuer_id, source, -- UI/API/AGENT/JOB
  created_at
)
```

### 2.4 服务行为

1. `previewCode(objectType, context)` → 不占号  
2. `allocateCode(...)` → 事务内锁序列 + 唯一校验 + 写 log  
3. 冲突自动重试 N 次；失败进异常队列  
4. 智能体「建档」只调 allocate，不自创字符串  
5. **精益**: 创建即编号，消灭「先空着以后补」

### 2.5 灯具品类字典驱动段

`sys_dict`: `product_type` → 编码段 `FL/WL/HB/STR/…`；功率档、客户简码可从项目/客户主数据取。

---

## 3. 数据自检体系（Data Quality）

### 3.1 三级结果

| 级别 | 行为 | 例 |
|------|------|-----|
| **BLOCK** | 禁止提交/发布/生效 | BOM 环、缺必填、无 PartNo |
| **WARN** | 可继续但记债、进待办 | 缺英文名、缺 3D、毛重空 |
| **INFO** | 仅提示 | 建议补光学参数 |

所有结果 **落库**，不落库不算检过。

### 3.2 规则模型

```sql
plm_dq_rule (
  id, rule_code, name, object_type,           -- PART/BOM/ECN/FILE/PROJECT/MOLD...
  phase_scope, lifecycle_scope,               -- 适用阶段/状态
  severity,                                   -- BLOCK/WARN/INFO
  check_type,                                 -- FIELD/SQL/SPEL/SCRIPT/AGENT
  expression,                                 -- 表达式或脚本引用
  message_template, fix_hint, auto_fix_code, -- 自动修复策略码(可选)
  lean_waste_tag,                             -- 对应浪费类型
  enabled, version_no, owner_role
)

plm_dq_run (
  id, batch_no, trigger_type, -- REALTIME/PRE_TRANSITION/NIGHTLY/MANUAL/AGENT
  object_type, object_id, rule_id, severity, result, -- PASS/FAIL
  message, detail_json, suggested_fix_json,
  duration_ms, created_at
)

plm_dq_object_score (
  object_type, object_id, score_0_100,
  block_count, warn_count, info_count,
  last_run_at, trend_7d, updated_at
)

plm_dq_debt (  -- 质量债务看板
  id, object_type, object_id, rule_code, severity,
  status, -- OPEN/ACCEPTED/FIXED/WAIVED
  owner_id, due_date, waived_by, waive_reason,
  created_at, closed_at
)
```

### 3.3 内置规则包（灯具最小集）

| 规则包 | 规则示例 |
|--------|----------|
| PART_BASE | 名称非空、分类合法、编码符合规则、禁止手工脏码 |
| PART_FG_LIGHT | 成品必填 IP/功率/电压；外贸必填 name_en/HS |
| BOM_STRUCT | 无环、qty>0、子件存在且未作废、关键件有价(WARN) |
| BOM_RELEASE | 发布前 BLOCK：空 BOM、禁用关键件 |
| DOC_GATE | 发布前门禁节点文件齐套 |
| ECN_EFFECT | 生效前 impact 非空、版本可解析、无并行 EFFECTING |
| PROJECT_GATE | G 门证据链接有效 |
| MOLD | 开模前图纸 RELEASED |
| LEAN_WIP | 个人 DRAFT Part > N 则 WARN/限制新建 |

### 3.4 触发点（自动化）

```
保存草稿     → 实时轻量检（字段级）
状态转换前   → 全量门禁检（与 lifecycle guard 合一）
ECN 生效前   → 特检
每晚 Job     → 全库扫描 → 债务清单 → 度量
智能体巡检   → 按风险抽样 + 根因摘要
发布/对接EBMS前 → CBOM/状态一致性
```

### 3.5 自动修复（精益：减少返工）

| auto_fix_code | 动作 |
|----------------|------|
| FILL_DEFAULT_UNIT | 单位缺省 pcs |
| GEN_ARCHIVE_TREE | 缺树则生成 |
| SYNC_NAME_FROM_PROJECT | 名称回填 |
| REBUILD_LTREE_PATH | 重建 path |
| ATTACH_TEMPLATE_PARAMS | 按品类套参数模板 |
| 不可自动 | 生成 ISSUE 派给 owner |

---

## 4. 度量 · KPI · OKR 数据底座（全落库）

> **原则**: 先有事实表，再有目标，再有智能分析。禁止「只存在于报表 Excel」。

### 4.1 指标分层

```
L0 事件事实  plm_fact_* / domain_event / operation_log
L1 原子指标  plm_metric_def + plm_metric_value
L2 KPI       plm_kpi_def + plm_kpi_value（可对标岗位/部门）
L3 OKR       plm_okr_objective + plm_okr_key_result + 关联 KPI
L4 改善成效  plm_improve_result 回写 KPI 前后差值
```

### 4.2 核心表设计

```sql
-- 指标字典（企业指标目录）
plm_metric_def (
  id, metric_code, name, description,
  category,          -- QUALITY/DELIVERY/COST/CYCLE/LEAN/SAFETY/AGENT
  unit, direction,   -- HIGHER_BETTER / LOWER_BETTER
  calc_type,         -- SQL/STREAM/FORMULA
  calc_expr,         -- SQL 或公式
  grain,             -- DAY/WEEK/MONTH/OBJECT
  dimensions_json,   -- ["dept","product_type","owner"]
  source_events,     -- 依赖事件
  enabled
)

-- 指标事实（按日/对象）
plm_metric_value (
  id, metric_code, grain_time,           -- 2026-07-22
  dim_json,                              -- {"dept":"RD","product_type":"FL"}
  object_type, object_id,                -- 可选对象级
  value_num, value_den, value_calc,      -- 支持率类分子分母
  batch_no, created_at
)

-- KPI 定义（管理口径）
plm_kpi_def (
  id, kpi_code, name, metric_code,       -- 绑定原子指标或公式
  owner_role, owner_user_id, org_id,
  target_type,                           -- ABSOLUTE/RANGE/IMPROVE_PCT
  period_type,                           -- MONTH/QUARTER/YEAR
  threshold_green, threshold_yellow, threshold_red,
  linked_okr_kr_id, enabled
)

plm_kpi_value (
  id, kpi_code, period_key,              -- 2026-Q3 / 2026-07
  target_value, actual_value, score, status, -- GREEN/YELLOW/RED
  dim_json, comment, locked, updated_at
)

plm_kpi_target_hist ( ... )              -- 目标调整历史，防「改目标美化」

-- OKR
plm_okr_cycle (id, name, start_date, end_date, status)
plm_okr_objective (
  id, cycle_id, org_id, owner_id, title, level, -- COMPANY/DEPT/PERSON
  status, progress_pct, parent_id
)
plm_okr_key_result (
  id, objective_id, title, metric_code, kpi_code,
  baseline, target, current_value, unit, weight,
  status, last_sync_at
)
plm_okr_checkin (
  id, kr_id, checkin_date, value, confidence, note, user_id
)

-- 指标与业务对象血缘（后续对账/下钻）
plm_metric_lineage (
  metric_code, source_table, source_event, transform_desc
)
```

### 4.3 预置 KPI 目录（可直接进库）

| kpi_code | 名称 | 方向 | 默认周期 | 数据来源 |
|----------|------|------|----------|----------|
| KPI_DQ_SCORE | 主数据质量均分 | ↑ | 周 | dq_object_score |
| KPI_BLOCK_RATE | 发布阻断率 | ↓ 看过程能力 | 周 | dq_run |
| KPI_CODE_AUTO_RATE | 自动编号率 | ↑ | 月 | code_issue_log |
| KPI_ECN_CYCLE_H | ECN 平均闭环小时 | ↓ | 月 | ecn 时间戳 |
| KPI_GATE_OTD | 阶段门准时率 | ↑ | 月 | project_gate |
| KPI_BOM_REWORK | BOM 发布后 7 日回退次数 | ↓ | 月 | lifecycle log |
| KPI_DOC_COMPLETE | 技转包一次齐套率 | ↑ | 月 | dq + tech package |
| KPI_OBSOLETE_HIT | 作废件被引用次数 | ↓ | 月 | where-used job |
| KPI_OUTSRC_LEAK | 外协越权/过期下载 | ↓ | 月 | download log |
| KPI_WIP_DRAFT | 超龄草稿料号数 | ↓ | 周 | part lifecycle |
| KPI_AGENT_ADOPT | 智能体建议采纳率 | ↑ | 月 | agent_action |
| KPI_IMPROVE_CLOSE | 改善单按时关闭率 | ↑ | 月 | improve |
| KPI_CBOM_SYNC | 与 EBMS 结构同源率 | ↑ | 周 | reconcile job |
| KPI_LOCK_VER | 大货订单锁版本率 | ↑ | 月 | integration |

OKR 示例（研发部季度）:

- **O**: 缩短新品可接单周期  
  - KR1: 阶段门准时率 ≥ 85%（KPI_GATE_OTD）  
  - KR2: ECN 周期 ≤ 72h（KPI_ECN_CYCLE_H）  
  - KR3: 质量均分 ≥ 90（KPI_DQ_SCORE）

### 4.4 采集自动化

| Job | 频率 | 输出 |
|-----|------|------|
| metric_daily_rollup | 每日 01:00 | metric_value 日粒度 |
| kpi_period_close | 日/周/月 | kpi_value + 红黄绿 |
| okr_sync_from_kpi | 每日 | KR current_value |
| anomaly_detect | 每日 | 异常事件 → 分析师智能体 |
| reconcile_ebms | 每日 | KPI_CBOM_SYNC |

---

## 5. 智能分析与问题改善闭环

### 5.1 分析服务（Analytics）

```sql
plm_analytics_dataset (
  id, dataset_code, name, sql_text, cache_ttl_sec, ...
)

plm_analytics_chart (
  id, chart_code, title, dataset_code,
  chart_type,     -- TREND/BAR/PIE/HEAT/FUNNEL/PARETO/SCATTER
  encode_json,    -- ECharts option 模板
  default_filters, refresh_cron, owner_role
)

plm_analytics_insight (
  id, insight_no, source,          -- JOB/AGENT/USER
  title, severity, category,
  finding_json,                    -- 数据发现
  root_cause_json,                 -- 根因假设
  recommendation_json,             -- 改善建议
  related_kpi_codes[],
  related_object_refs[],
  status,                          -- NEW/ACK/CONVERTED_ISSUE/DISMISSED
  agent_session_id, created_at
)
```

**预置图表（驾驶舱）**:

1. 生命周期漏斗（DRAFT→…→MP）  
2. ECN 周期趋势 + 帕累托（变更原因）  
3. 质量债务热力（规则 × 部门）  
4. 阶段门延误看板  
5. BOM 复杂度 vs 变更频次散点  
6. 自动编号率 / 自检通过率趋势  
7. 智能体采纳率与节省工时估算  
8. KPI 红黄绿一页纸  
9. OKR 进度树  
10. 外协下载与风险  

### 5.2 问题与改善（A3 精益）

```sql
plm_issue (
  id, issue_no, source_type, -- DQ/INSIGHT/USER/ECN/GATE/AGENT
  source_ref, object_type, object_id,
  title, description, category, severity,
  status, -- OPEN/ANALYZING/ACTION/VERIFY/CLOSED/CANCEL
  owner_id, dept_id, due_date,
  lean_waste_tag, kpi_codes[],
  created_at, closed_at
)

plm_issue_analysis (  -- 5Why / 鱼骨
  id, issue_id, method, content_json, created_by, created_at
)

plm_improve_action (
  id, action_no, issue_id, title, action_type, -- CORRECTIVE/PREVENTIVE/STANDARDIZE
  owner_id, due_date, status, evidence_url,
  standard_doc_id,   -- 标准化回写（作业指导/规则）
  created_at, done_at
)

plm_improve_result (
  id, issue_id, kpi_code,
  before_value, after_value, window_from, window_to,
  verified_by, verified_at, effective  -- 是否真改善
)

plm_standard_work (  -- 标准作业/对策固化
  id, sw_no, title, object_type, content, version_no,
  from_issue_id, status, published_at
)
```

**闭环状态机（精益 PDCA）**:

```
问题发现(自检/分析/人/智能体)
  → OPEN → ANALYZING(5Why) → ACTION(对策)
  → VERIFY(对比 KPI 前后) → CLOSED
  → 固化 STANDARD_WORK / DQ规则 / 门禁
```

### 5.3 「分析问题 → 提出改善」自动链

```
anomaly_detect / 质检官巡检
  → 写 plm_analytics_insight
  → 分析师智能体：补充根因与建议
  → 若 severity≥HIGH：自动开 plm_issue 并指派 owner
  → 改善教练智能体：拆解 action、预估影响 KPI
  → 人确认执行 → VERIFY Job 对比 metric
  → 成功则建议固化规则（人审批后生效）
```

---

## 6. 智能体体系（Agent）

### 6.1 定位

- **不是聊天玩具**，是带权限的 **Tool-using Worker**。  
- 所有建议、工具调用、采纳/驳回 **必须落库**，进 KPI。  
- 默认 **人在回路**：BLOCK 级变更必须人确认；只读分析可自动。

### 6.2 智能体清单

| agent_code | 名称 | 职责 | 主要工具 |
|------------|------|------|----------|
| AGENT_CODER | 编码助手 | 预览/申请编号、解释规则 | CodeGen API |
| AGENT_QA | 质检官 | 跑规则、解释失败、建议修复 | DQ API、auto_fix |
| AGENT_ANALYST | 分析师 | 出图表解读、异常洞察 | Analytics、Metric |
| AGENT_COACH | 改善教练 | 5Why 辅助、拆改善动作、估 KPI | Issue/Improve API |
| AGENT_ECN | 变更助理 | 影响面草稿、ECN 描述、检查清单 | BOM diff、where-used、ECN |
| AGENT_GATE | 技转检查员 | 齐套检查、放行风险 | Archive、DQ、Project |
| AGENT_BOM | 结构助理 | 提取/规范 BOM、发现重复件 | BOM、Algorithm |
| AGENT_HELP | 问答员 | 制度/SOP/字段说明 | 知识检索、StandardWork |
| AGENT_ORCH | 总控 | 路由意图、多代理协作 | 全部 |

### 6.3 智能体数据模型

```sql
plm_agent_def (
  id, agent_code, name, description,
  model_config_json,          -- 模型/温度/超时
  tool_allowlist_json,        -- 允许的 tool 名
  permission_scope,           -- 模拟角色 ENGINEER 等
  autonomy_level,             -- L0建议 L1只读写草稿 L2有限自动 L3禁止
  enabled, version_no
)

plm_agent_session (
  id, session_no, agent_code, user_id,
  channel,                    -- UI/JOB/API
  context_json, status, started_at, ended_at
)

plm_agent_message (
  id, session_id, role,       -- user/assistant/tool/system
  content, token_usage, created_at
)

plm_agent_tool_call (
  id, session_id, message_id, tool_name,
  request_json, response_json, success, duration_ms, created_at
)

plm_agent_action (
  id, session_id, action_type, -- SUGGEST/AUTO_FIX/CREATE_ISSUE/DRAFT_ECN...
  object_type, object_id,
  payload_json, status,       -- PROPOSED/APPROVED/REJECTED/EXECUTED/FAILED
  decided_by, decided_at, result_json,
  estimated_minutes_saved
)

plm_agent_feedback (
  id, session_id, action_id, score, comment, created_by, created_at
)

plm_agent_knowledge (         -- 可审知识
  id, title, category, content, source_ref, status, updated_at
)
```

### 6.4 自主级别（安全）

| 级别 | 可做 | 不可做 |
|------|------|--------|
| L0 | 解释、检查、生成建议文案 | 任何写库 |
| L1 | 写草稿、开 ISSUE 草稿、预览编号 | 发布/生效/删 |
| L2 | auto_fix 白名单、分配编号、夜间关 INFO 债 | ECN 生效、权限改 |
| L3 | （本企业默认关闭） | — |

### 6.5 与业务咬合的典型剧本

**剧本 A — 一键建成品（自动化）**  
用户: 新品投光灯 100W 客户 354  
→ CODER 申请 part_no  
→ 套灯具参数模板  
→ 生成档案树  
→ QA 自检  
→ 质量分展示；BLOCK 则阻断进入下一页  

**剧本 B — 发布前**  
→ GATE+QA 齐套  
→ 失败项一键生成债务/问题  
→ 通过才点亮「发布」  

**剧本 C — 夜间精益会**  
→ ANALYST 跑异常  
→ 生成 insight 榜  
→ COACH 对 TOP5 出改善草案  
→ 早会看板直接开  

**剧本 D — ECN**  
→ ECN 助理 where-used + diff  
→ 填 impact  
→ QA 跑生效前检  
→ 人点生效  

### 6.6 模型与成本控制（中小企业）

- 优先：**规则引擎 > 小模型 > 大模型**。  
- 字段校验不走 LLM。  
- LLM 用于：根因叙述、改善建议、ECN 描述、知识问答。  
- 可配置本地/云 API Key；无 Key 时智能体降级为「规则+模板话术」。

---

## 7. 精益运营机制（系统内建）

### 7.1 待办拉动工作台（替代「人肉催」）

```sql
plm_work_item (
  id, item_no, type, -- APPROVAL/DQ_DEBT/GATE/ECN/IMPROVE/AGENT_SUGGEST
  title, ref_type, ref_id, priority,
  owner_id, status, sla_due_at, escalated_to,
  created_at, completed_at
)
```

- 个人 WIP 上限（如工程师未结待办 > 20 WARN）。  
- SLA 超时自动升级主管（通知 + 记 metric）。  

### 7.2 标准作业与一次做对

- 建档/发布/技转/ECN 四条 **Standard Work** 内置检查清单。  
- UI 向导逐步，不允许跳过 BLOCK。  

### 7.3 可视化管理

- 电子安灯：质量分 < 阈值、门禁红灯、ECN 超时 → 驾驶舱闪烁。  
- 帕累托自动出「本周最大返工来源」。  

---

## 8. 模块增量清单（相对 V1.0）

| 模块 | V1.1 新增 |
|------|-----------|
| M0 横切 | CodeGen、DQ 引擎、Metric/KPI/OKR、Insight、Issue/Improve、Agent、WorkItem |
| M1 主数据 | 建档向导全自动编号+模板+自检评分 |
| M2 生命周期 | 转换前强制 DQ；结果写入 run 表 |
| M3 项目 | 门禁证据自检；延误自动 insight |
| M4 BOM | 保存/发布自检；重复件/环智能提示 |
| M5 变更 | ECN 助理；生效后自动度量 |
| M6 文档 | 齐套规则包；缺件自动债 |
| M7–M11 | 各域规则包 + 图表 |
| M12 集成 | 对账指标入库；失败自动 ISSUE |
| M13 系统 | 规则/KPI/Agent 配置台 |
| **M15 精益驾驶舱** | 新：KPI/OKR/债务/安灯/智能体成效 |
| **M16 改善中心** | 新：问题-分析-对策-验证 |
| **M17 智能体中心** | 新：会话、工具审计、采纳率 |

---

## 9. API 体系（防后期重复开接口）

> **完整契约见**: [`docs/plm-api-spec-v1.1.md`](./plm-api-spec-v1.1.md)  
> 原则：**领域管对象 · Query 管清单 · Metric/KPI 管数 · Export 管表 · Webhook 管变**。

### 9.1 六层通道（取数只走这些，禁止 `/report/*` 膨胀）

| 层 | 前缀 | 以后取数怎么用 |
|----|------|----------------|
| A 领域 | `/api/v1/parts\|boms\|ecns\|…` | 单对象读写与 actions |
| B 通用查询 | `/api/v1/query` `/lookup` `/search` `/graph` | 任意列表/批量/关系，**加白名单不加路由** |
| C 度量目标 | `/api/v1/metrics/query` `/kpis` `/okrs` | 趋势/KPI/OKR **唯一出口** |
| D 分析导出 | `/api/v1/analytics/*` `/exports` | 图表配置化；Excel/BI 异步导出 |
| E 集成事件 | `/api/v1/integration/*` + Webhook | EBMS/ERP；变更推送少轮询 |
| F 自动化 | `/codegen` `/dq` `/agents` `/issues` | 编号/自检/智能体/改善 |

### 9.2 关键只读契约（EBMS/BI 必接）

```
GET  /api/v1/integration/parts/{partNo}
POST /api/v1/integration/parts/lookup
GET  /api/v1/integration/parts/{partNo}/cbom
POST /api/v1/integration/orders/lock-version
POST /api/v1/metrics/query
GET  /api/v1/kpis/values
POST /api/v1/exports
Webhook: part.state_changed | ecn.effective | kpi.updated
```

### 9.3 工程门禁

- OpenAPI: `GET /api/v1/openapi.json` 为唯一契约真相  
- Code Review: 新增 `/stats` `/report` `/dashboard` 若未转调 metric/query → 拒合  
- 旧 `/api/material` 等兼容转发 12 个月并打废弃头

---

## 10. 前端信息架构增量

```
工作台
  我的待办(拉动) / 安灯 / 今日智能摘要

精益与质量
  数据质量债务 / 自检规则 / 质量分排行

分析驾驶舱
  KPI 一页纸 / 图表市场 / 洞察列表

OKR
  周期 / 目标树 / KR 打卡（只读对接可后期接企业 OKR）

改善中心
  问题看板 / A3 / 成效验证 / 标准作业库

智能体
  对话 / 巡检任务 / 建议待确认 / 成效统计

（原 V1.0 业务菜单保持）
```

---

## 11. 事件总线（自动化中枢）

所有自动化靠领域事件驱动，事件入 `plm_domain_event`（Outbox）:

| 事件 | 消费者 |
|------|--------|
| part.created | 档案树、DQ、metric |
| part.before_release | DQ BLOCK 门禁 |
| part.released | KPI、EBMS 通知 |
| ecn.effective | 版本、水印任务、EBMS 重算、metric |
| dq.failed_block | work_item、可选 agent |
| metric.anomaly | insight、coach agent |
| gate.overdue | escalate、insight |
| agent.action.executed | feedback metric |
| improve.verified | KPI 回写、standard 建议 |

---

## 12. 实施路线调整（在 V1.0 Wave 上插入）

| 波次 | 内容 | 周期 |
|------|------|------|
| **W0** | 生命周期引擎 + **CodeGen + DQ 框架表/API** + 事件 Outbox | 5–6 周 |
| **W1** | 主数据向导（自动编号+自检评分）+ 档案 | 5 周 |
| **W2** | BOM + CBOM + **度量日批** + 基础驾驶舱图表 | 6–7 周 |
| **W3** | ECN + **Issue/Improve 闭环** + KPI 目录上线 | 6 周 |
| **W4** | 项目门禁 + 3D/模具/工艺 + 规则包扩展 | 8 周 |
| **W5** | 外协贸易 + **Agent L0/L1**（质检官/编码/问答） | 6 周 |
| **W6** | OKR 模块 + Agent 分析师/改善教练 + EBMS 指标对齐 | 6 周 |
| **W7** | 精益深化（WIP/SLA/标准作业）+ 成效审计 | 持续 |

**比 V1.0 更早植入 CodeGen/DQ/Metric**，避免后期「报表空中楼阁」。

---

## 13. 验收标准（V1.1 增量）

| # | 标准 |
|---|------|
| 1 | 新料号 100% 经 CodeGen 分配，issue_log 可追溯 |
| 2 | 任何 RELEASE 动作前有 dq_run 记录；BLOCK 无法绕过（除双人特批留痕） |
| 3 | 预置 ≥ 30 条 DQ 规则、≥ 14 个 KPI 每日有值 |
| 4 | 驾驶舱 10 张核心图数据来自 DB 非写死 |
| 5 | 高严重洞察可一键转 ISSUE；改善验证写 improve_result |
| 6 | 智能体工具调用全审计；采纳率指标可查 |
| 7 | OKR KR 与 KPI 数值自动同步，支持 checkin |
| 8 | 夜间批检 + 早会洞察清单可导出 |

---

## 14. 与 EBMS / KPI 治理衔接

| 层级 | PLM | EBMS | 企业 OKR |
|------|-----|------|----------|
| 工程过程 KPI | **权威** | 只读嵌入 | 可拉取 |
| 订单毛利/费用 | 提供 CBOM | **权威** | 可拉取 |
| 部门 OKR | 研发/工艺/品质过程 KR | 经营/销售 KR | 汇总层 |
| 改善成效 | 工程侧 before/after | 经营侧利润影响 | 季度复盘 |

对接方式：`GET /api/kpis/export?period=`、Webhook `kpi.updated`；字段统一 `kpi_code`。

---

## 15. 风险与精益约束

| 风险 | 约束 |
|------|------|
| 智能体乱写数据 | 默认 ≤L1；白名单工具；双人生效 |
| 规则过多卡死业务 | WARN/BLOCK 分级；特批+债务 |
| KPI 弄虚作假 | 目标变更历史；事实表只追加 |
| LLM 成本 | 规则优先；缓存；限流 |
| 指标膨胀 | 指标目录评审制；不用的 enabled=false |

---

## 16. 总结：V1.0 → V1.1 进化点

| 维度 | V1.0 | V1.1 |
|------|------|------|
| 编码 | 有序列思想 | **规则化自动编号 + 全日志** |
| 质量 | 门禁列表 | **规则引擎 + 评分 + 债务 + 夜检** |
| 管理 | 模块功能 | **Metric→KPI→OKR 四层落库** |
| 分析 | 仪表盘统计 | **图表+洞察+根因+改善闭环** |
| 智能 | AI 建模点缀 | **多智能体工具化 + 审计 + 采纳 KPI** |
| 精益 | 提及 | **WIP/SLA/安灯/标准作业/七浪费标签** |
| 自动化 | 事件与集成 | **事件驱动作业 + 自动修复 + 自动开单** |

---

## 17. 配套产物

| 文件 | 说明 |
|------|------|
| `docs/plm-complete-redesign-lighting-sme.md` | V1.0 业务与模块基线 |
| `docs/plm-complete-redesign-lighting-sme-v1.1.md` | **本文** |
| `docs/plm-api-spec-v1.1.md` | **API 总规（取数/集成/防接口膨胀）** |
| `database/10_v1_1_lean_agent_kpi.sql` | 表结构草案（可直接评审） |

---

*V1.1 是建设时的「操作系统层」：业务模块跑在自动编号、自检、度量与智能体之上，而不是上线后再补报表。*
