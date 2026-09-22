# 恒剑光电 PLM 统一中台 V5.0 — 整体架构设计

> 文档编号: HJ-PLM-V5.0-ARCH-20260722  
> 目标: 合并 `D:\PLM`（localhost:3000 三维爆炸全链路）与 `D:\PLM-2`（企业级管控底座），补齐生命周期断层，形成单一可落地架构。

---

## 0. 合并背景与结论

### 0.1 两套系统定位

| 维度 | A 系统 `D:\PLM` (:3000) | B 系统 `D:\PLM-2` | 合并策略 |
|------|------------------------|------------------|----------|
| 产品定位 | 3D 装配爆炸 + 外贸/AI 全链路演示 | 物料/ECN/档案/外协/模具企业管控 | **B 为底座，A 为能力包** |
| 主锚点 | ProductArchive（产品料号） | Material（物料 PartNo） | 统一为 **PartNo + 对象类型** |
| 技术栈 | 设计 .NET6+SQLite；运行 Python mock | Java17 + PG + Vue3 + FastAPI | **保留 B 栈**，吸收 A 领域模型与前端页面 |
| BOM | EBOM/MBOM/SBOM 三态 | 多级树 + ltree（未用） | 三态 + 多级树并存 |
| ECN | 对比/刷新/冻结（无审批） | L1/L2 审批 + 生效（副作用残缺） | **审批闭环 + 对比刷新** |
| 3D | Web3D 爆炸 + 批量任务 + 模板 | Model3D 库 + trimesh/AI | 统一 3D 算法服务 |
| 安全 | 七级角色标签（弱） | RBAC + JWT + 水印 + 内外网 | **B 安全模型** |
| 生命周期 | Trial/Official/Revised（浅） | 多枚举但断层 | **统一状态机引擎** |

### 0.2 核心决策（一句话）

> **以 PLM-2 的 Java/PostgreSQL/RBAC/文件安全为运行底座，迁入 PLM(:3000) 的产品档案、BOM 三态、3D 爆炸任务、工艺/外贸/AI/装箱能力，并用统一生命周期引擎打通「新品→量产→变更→封存」全链路。**

---

## 1. 目标业务全景

```
新品立项 ──► 结构设计 ──► 3D爆炸/EBOM ──► 模具开发 ──► 试模/工艺 ──► 量产发布
   │              │            │              │            │            │
   │              ▼            ▼              ▼            ▼            ▼
   │         档案目录树    MBOM/SBOM      模具台账     SOP/DFM/IQC    ERP/MES
   │         图纸/3D      Web3D交互      试模履历     外协发图        外贸输出
   │
   └──────── ECN/ECR 变更闭环（对比→审批→生效→全域刷新→旧版冻结）────────┘
                              │
                              ▼
                    版本快照 / 作废水印 / 封存归档
```

### 1.1 用户门户（多端同源）

| 门户 | 来源 | 能力子集 |
|------|------|----------|
| 研发内网完整版 | B + A 合并 | 全模块 |
| 工艺/品质端 | A process + B quality | SOP/DFM/检验/模具只读 |
| 销售轻量端 | A trade 脱敏 | 外观 3D + 成品 BOM + 手册 |
| 车间打印端 | A process | SOP/工艺卡/装箱 |
| 外协端 | B outsourcing | 审批链接 + 水印下载 |
| 客户外网端 | B share + A web3d 脱敏 | Token 预览 |

---

## 2. 统一技术架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Vue3 + Element Plus + Pinia + Three.js（统一前端 monorepo / 多 portal）   │
│  products | bom(三态+树) | ecn | process | mold | archive | trade | ai   │
│  model3d | outsourcing | packing | jobs | templates | system            │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS + JWT + 操作日志
┌───────────────────────────────▼─────────────────────────────────────────┐
│              plm-backend  Java 17 / Spring Boot 3（领域服务中枢）           │
│  ┌────────────┬────────────┬────────────┬────────────┬────────────────┐ │
│  │ identity   │ lifecycle  │ product    │ change     │ document       │ │
│  │ RBAC/JWT   │ 状态机引擎  │ 料号/参数   │ ECR/ECN    │ 档案/文件/水印  │ │
│  ├────────────┼────────────┼────────────┼────────────┼────────────────┤ │
│  │ structure  │ process    │ tool       │ commerce   │ integration    │ │
│  │ BOM三态+树  │ SOP/DFM/Q  │ 模具/工装   │ 外贸/装箱  │ ERP/MES Adapter│ │
│  └────────────┴────────────┴────────────┴────────────┴────────────────┘ │
│  事件总线(Redis Stream/本地 Outbox) + 任务队列 + 审计 AOP                   │
└───────────┬─────────────────────────────┬───────────────────────────────┘
            │                             │
┌───────────▼───────────┐     ┌───────────▼───────────────────────────────┐
│ plm-algorithm         │     │ PostgreSQL 15 + Redis 7                    │
│ FastAPI + trimesh     │     │ ltree / JSONB 快照 / 全文检索               │
│ + OCCT 内核(可选)     │     │ 版本历史 / 流程日志 / 文件元数据            │
│ 爆炸/装配树/DFM几何   │     └───────────────────────────────────────────┘
│ AI建模/排版/成本/风险 │
└───────────────────────┘
            │
     Creo/NX 插件（可选，HTTP 调算法服务）
```

### 2.1 技术选型定稿

| 层级 | 选型 | 理由 |
|------|------|------|
| 前端 | Vue3 + Vite + Element Plus + Three.js | 两套前端同栈，易合并页面 |
| 主后端 | **Java Spring Boot 3**（PLM-2） | 已有 RBAC/事务/审计，适合企业管控 |
| 算法 | **Python FastAPI**（合并 PLM-2 algorithm + PLM occt mock 契约） | 3D/AI 生态；保留 `/explode` `/job` 协议兼容 A |
| DB | **PostgreSQL + ltree + JSONB** | 替换 A 的 SQLite；支撑树与版本快照 |
| 缓存/队列 | Redis（真用：会话、分布式锁、任务队列、事件） | PLM-2 已配未用，本次落地 |
| 文件 | 内外网双目录 + 水印引擎（B） | 安全底座不可丢 |
| 部署 | docker-compose 五服务 | frontend / backend / algorithm / postgres / redis |

### 2.2 不采用的方案

- ❌ 双后端并行（Java + .NET）长期共存 → 运维与事务分裂  
- ❌ 以 SQLite 为生产库  
- ❌ 继续「硬编码 if 改状态」而无统一状态机  
- ❌ 产品与物料两套主数据互不映射  

---

## 3. 统一领域模型（核心）

### 3.1 主数据：Part 统一物料/产品

A 的 `ProductArchive` 与 B 的 `Material` 合并为 **`plm_part`**：

| 字段 | 来源 | 说明 |
|------|------|------|
| `part_no` | A+B | 全局唯一主键 |
| `name_zh` / `name_en` | A | 中英文名 |
| `part_category` | 新 | PRODUCT / ASSEMBLY / COMPONENT / STANDARD / PACKAGE |
| `product_type` | A | 投光灯/工作灯…（成品分类） |
| `material_type` | B | 原料/半成品/成品等字典 |
| `revision` | A | 显示版次 A/B/C 或 V1.0（规则统一） |
| `version_no` | B | 规范版本串 `V{major}.{minor}` |
| `lifecycle_state` | **新·唯一状态** | 见 §4，废除双字段 |
| `phase` | 新 | 项目阶段：CONCEPT/STRUCTURE/MOLD/TRIAL/MP/EOL |
| `model_3d_path` | A | 主 3D 路径 |
| `template_id` | A | 爆炸模板 |
| `owner_id` / `org_id` | B | 责任人/组织 |

**零件参数** `plm_part_param`（来自 A `PartParam`）：材质、RoHS、IP、供应商、检验规范、HS、库存码、单价、MOQ — 绑定 `part_no` + `version_no`。

**原则：**  
- 成品、组件、标准件全部是 Part；`part_category` 区分。  
- 前端 `/products` = 筛选 `part_category in (PRODUCT, ASSEMBLY)` 的档案库视图。  
- B 原 `/material` 路由兼容重定向到 `/products` 或保留为「全量物料」视图。

### 3.2 结构：BOM 三态 + 多级树

```
plm_bom                — bom_id, root_part_no, bom_type(EBOM|MBOM|SBOM), version_no, state, ecn_no
plm_bom_item           — parent_item_id, child_part_no, qty, path(ltree), process_op, sbom_class, ...
plm_bom_version        — 快照 JSONB（强制写入）
```

| 类型 | 来源能力 | 构建方式 |
|------|----------|----------|
| EBOM | A extract + B 树编辑 | 算法装配树提取 **或** 人工维护 |
| MBOM | A BuildMbom | 工序重构 + 紧固件打包；可人工微调 |
| SBOM | A BuildSbom | A/B/C 分级；售后/外贸引用 |

**规则：**  
- 发布后的 BOM 只读；变更必须经 ECN。  
- `path` ltree **必须写入**（修复 B 空洞）。  
- where-used：`path @> ` / 反向查询 child_part_no。

### 3.3 变更：ECR + ECN

```
plm_ecr          — 变更请求（问题描述/影响初判/提出人）
plm_ecn          — 变更通知（吸收 B 审批字段 + A 的 old/new rev、changed_parts）
plm_ecn_impact   — 影响对象清单（PART/BOM/FILE/MOLD/SOP/TRADE_DOC）
plm_ecn_flow_log — 审批时间线（B）
plm_ecn_attachment
```

**ECN 能力合并：**

| 能力 | A | B | V5 |
|------|---|---|-----|
| 版本 diff 标红 | ✅ | ❌ | ✅ |
| L1/L2 审批 | ❌ | ✅ | ✅ |
| 生效升版 | 部分 | 有 bug | ✅ 事务落库 |
| 全域刷新 | ✅ | ❌ | ✅ 按 impact |
| 旧版冻结/作废水印 | 弱 | 半实现 | ✅ |
| 附件 | ❌ | 表空 | ✅ |

### 3.4 文档与档案

保留 B 的固定档案树模板 + 文件安全：

```
plm_archive_tree / plm_archive_file / plm_file
```

每个文件具备：`version_no`、`doc_state`、`visibility`、`obsolete`、`watermark`。  
文档状态走统一生命周期（§4），禁止物理删除。

### 3.5 工艺 / 品质 / 模具 / 外贸 / AI / 任务

| 域 | 表/对象 | 来源 |
|----|---------|------|
| 工艺 | `plm_sop`, `plm_dfm_report`, `plm_fixture_reco` | A Process |
| 品质 | `plm_inspection_standard`（扩展 IQC/IPQC/FQC） | A+B |
| 模具 | `plm_mold`, `plm_mold_trial_log`, `plm_injection_sop` | B |
| 外协 | `plm_outsource_*` | B |
| 外贸 | `plm_trade_package`, manuals/ecom/quote 产物路径 | A |
| 装箱 | `plm_packing_result` | A |
| AI | 请求日志 + 结果 JSONB（成本/风险/排版/模板匹配） | A |
| 3D 任务 | `plm_batch_job`（queued/running/done/failed） | A |
| 模板 | `plm_explode_template`（JSON，LED 模板库迁入） | A |
| 分享 | `plm_share_link` | B |

---

## 4. 统一生命周期引擎（补齐原不足）

### 4.1 设计原则

1. **单一状态字段** `lifecycle_state`（废除 material.status + lifecycle_status 双轨）。  
2. **配置化转换矩阵**（表驱动，非散落 if）。  
3. **转换 = 守卫 + 动作 + 事件**（事务内）。  
4. **所有受控对象共用引擎**：Part / Bom / File / Sop / Mold / Ecn（Ecn 可用专用流）。  

### 4.2 对象状态集

**Part / Bom / File / Sop 主状态：**

```
DRAFT → IN_REVIEW → RELEASED → IN_PRODUCTION
                       │            │
                       ├─► CHANGING ┘（ECN 生效中）
                       ├─► OBSOLETE
                       └─► SEALED
```

**Phase（项目阶段，正交维度，不替代状态）：**

```
CONCEPT → STRUCTURE → MOLD_DEV → TRIAL → MASS_PRODUCTION → EOL
```

**ECN 专用流（保留 B，增强）：**

```
DRAFT → PENDING_L1 → PENDING_L2 → APPROVED → EFFECTING → EFFECTIVE
                 ↘ REJECTED ↗                └→ FAILED
任意非终态 → VOID
```

### 4.3 转换表示例（`plm_lifecycle_transition`）

| object_type | from | to | action_code | roles | guards | side_effects |
|-------------|------|-----|-------------|-------|--------|--------------|
| PART | DRAFT | IN_REVIEW | submit_review | ENGINEER | has_required_attrs | notify |
| PART | IN_REVIEW | RELEASED | release | RD_LEAD | has_ebom_released? optional | lock_edit, snapshot |
| PART | RELEASED | CHANGING | start_change | system | open_ecn | — |
| PART | CHANGING | RELEASED | ecn_effect | system | ecn_effective | bump_version, snapshot, obsolete_old_files |
| PART | RELEASED | IN_PRODUCTION | to_mp | MP_LEAD | phase>=TRIAL | phase=MP |
| PART | * | OBSOLETE | obsolete | RD_LEAD | no_active_where_used or force | watermark_lock |
| PART | OBSOLETE | SEALED | seal | ADMIN | — | archive_freeze |
| BOM | DRAFT | RELEASED | release | ENGINEER | no_cycle, qty>0 | write_bom_version |
| ECN | APPROVED | EFFECTIVE | effect | ECN_EFFECTOR | impacts_resolved | run_effect_pipeline |

### 4.4 生效管线（修复 B 的核心 bug）

```
ECN.effect()  @Transactional
  1. 校验 APPROVED
  2. 状态 → EFFECTING
  3. for impact in impacts:
       PART  → version_no++ 落库；写 plm_part_version 快照；
               CHANGING→RELEASED；旧文件 obsolete+水印
       BOM   → 按 change_type 重建/升版；写 plm_bom_version
       FILE  → 新版挂接；旧版冻结
       MOLD  → 状态/图纸联动
       SOP   → 标记需修订 or 自动 regenerate
       TRADE → 投递刷新任务（手册/备件）
  4. 投递领域事件 EcnEffectiveEvent
  5. ECN → EFFECTIVE；写 flow_log
```

**禁止**再通过「只改内存 + changeStatus 重载丢字段」升版。

### 4.5 发布门禁（Release Gate）

可配置检查项 `plm_release_gate`：

- 必填属性 / 必传档案节点  
- 至少 1 份 RELEASED 图纸或 3D（按品类）  
- EBOM 无环、数量合法  
- 关键进行中 ECN（可选）  
- DFM 无 CRITICAL（可选）  

未过门禁禁止 `release`。

---

## 5. 模块划分与 API 边界

### 5.1 后端包结构（在 PLM-2 上演进）

```
com.hjgd.plm
├── identity          # 用户/角色/权限/JWT（B）
├── lifecycle         # 【新】状态机、转换、快照、门禁
├── part              # 原 material + A products/params
├── bom               # 树 + 三态 + extract/mbom/sbom/export
├── change            # ecr + ecn + impact + effect pipeline
├── document          # archive + file + watermark + share
├── process           # sop/dfm/fixture/inspect（迁 A）
├── quality           # 原 quality 并入或并列
├── mold              # 模具（B）
├── outsourcing       # 外协（B）
├── commerce          # trade + packing（迁 A）
├── intelligence      # ai + template + batch job（迁 A）
├── model3d           # 模型库 + 代理 algorithm
└── integration       # ERP/MES 真适配
```

### 5.2 对外 API 地图（兼容两套前端路径）

| 前缀 | 能力 | 兼容 |
|------|------|------|
| `/api/products/**` | 产品档案 CRUD/导入/参数 | A `/products` |
| `/api/parts/**` | 全量物料（含组件） | B `/material` 别名 |
| `/api/bom/**` | 树 CRUD + `/extract` `/mbom` `/sbom` `/export` `/compare` | A+B |
| `/api/ecn/**` `/api/ecr/**` | 审批 + compare/refresh/freeze/effect | A+B |
| `/api/process/**` | sop/dfm/fixture/inspect | A |
| `/api/quality/**` | 检验标准 | B |
| `/api/mold/**` `/api/outsourcing/**` | 模具/外协 | B |
| `/api/trade/**` `/api/packing/**` | 外贸/装箱 | A |
| `/api/ai/**` `/api/jobs/**` `/api/templates/**` | AI/任务/模板 | A |
| `/api/model3d/**` `/api/explode` | 3D 与爆炸代理 | A+B |
| `/api/archive/**` `/api/files/**` `/api/share/**` | 档案文件分享 | B |
| `/api/lifecycle/**` | 通用状态流转/历史 | 新 |
| `/api/integration/**` | ERP/MES | A+B 预留落地 |

### 5.3 算法服务契约（统一）

保留 A 的 explode/job 协议，并入 B 的 3D/AI：

```
POST /v1/explode          # 装配树 + 爆炸位移 + 碰撞
POST /v1/jobs             # 异步批量
GET  /v1/jobs/{id}
POST /v1/dfm/check        # 几何类 DFM（可选 OCCT）
POST /v1/ai/*             # 成本/风险/排版/重建/建模
```

Java 侧 `AlgorithmClient` 统一调用；批量任务状态写 `plm_batch_job`，Redis 做队列。

---

## 6. 前端信息架构（合并菜单）

以 PLM-2 layout 为壳，迁入 A 页面：

```
工作台 Dashboard
产品
  ├─ 产品料号档案库      ← A ProductArchive（主入口，原 /products）
  ├─ 物料主数据          ← B material 全量
  └─ 零件参数 / 导入
结构与 3D
  ├─ BOM 三态管理        ← A BomManager
  ├─ BOM 多级树编辑      ← B bom/tree
  ├─ Web3D 交互爆炸      ← A Web3dExplorer
  ├─ 3D 模型库           ← B model3d
  ├─ 爆炸模板            ← A TemplateCenter
  └─ 批量任务            ← A BatchJobs
变更
  ├─ ECR 变更请求        ← 新
  └─ ECN 变更闭环        ← A 对比刷新 + B 审批时间线
工艺品质
  ├─ SOP / DFM / 工装    ← A ProcessManager
  └─ 检验标准            ← B quality
模具与外协
  ├─ 模具台账/试模       ← B mold
  └─ 外协发图            ← B outsourcing
档案与安全
  ├─ 档案目录树          ← B archive
  └─ 外网分享            ← B share
商业输出
  ├─ 外贸三册/电商       ← A TradeManager
  └─ 智能装箱            ← A PackingCalc
智能
  └─ AI 中心             ← A AiCenter
系统
  ├─ 用户/角色/字典/日志 ← B
  ├─ 生命周期配置        ← 新（转换矩阵/门禁）
  └─ 集成适配            ← B integration
```

路由别名：`/products` 为默认首页（对齐 A 用户习惯）。

---

## 7. 关键业务链路（合并后）

### 7.1 新品建档 → 爆炸 → EBOM

```
创建 Part(PRODUCT, DRAFT)
  → 生成档案树(B)
  → 上传/关联 3D
  → Job: explode(template)
  → 写回 transforms + assembly_tree
  → BOM.extract → EBOM(DRAFT)
  → 人工修订树 → release EBOM（过门禁）
  → build MBOM / SBOM
```

### 7.2 发布量产

```
Part IN_REVIEW → gate check → RELEASED
  → （可选）to_mp → IN_PRODUCTION, phase=MASS_PRODUCTION
  → 推送 ERP（integration）
  → 文件正式版锁定
```

### 7.3 变更闭环

```
ECR → 评估 → 转 ECN
  → compare(oldRev,newRev) 标红
  → 填 impact 清单
  → submit → L1 → L2 → APPROVED
  → effect 管线（版本/快照/作废/刷新）
  → freeze 旧版
  → 事件驱动 trade/ai 再生成
```

### 7.4 外协与客户

```
出图申请 → 审批 → 水印包 + 有效期 → 下载审计（B）
分享链接 → 脱敏 GLB → 访问日志（B + A web3d）
```

---

## 8. 数据迁移与兼容

### 8.1 从 A（:3000 / SQLite 或 mock 内存）

| A | V5 |
|---|-----|
| Products | plm_part (category=PRODUCT) |
| PartParams | plm_part_param |
| Boms/BomLines | plm_bom / plm_bom_item + bom_type |
| EcnOrders | plm_ecn + changed_parts→impact |
| BatchJobs | plm_batch_job |
| templates/*.json | plm_explode_template / 文件库 |

### 8.2 从 B（PostgreSQL 现网）

| B | V5 |
|---|-----|
| plm_material | plm_part；status∥lifecycle_status → lifecycle_state 映射 |
| plm_bom* | 补 bom_type 默认 EBOM；回填 ltree |
| plm_ecn* | 保留审批字段；补 impact |
| 其余表 | 保留演进 |

### 8.3 状态映射

| 旧 (B) | 旧 (A) | 新 lifecycle_state |
|--------|--------|-------------------|
| DRAFT | Trial | DRAFT |
| REVIEWING | — | IN_REVIEW |
| RELEASED | Official | RELEASED |
| IN_PRODUCTION | — | IN_PRODUCTION |
| CHANGING | Revised* | CHANGING |
| OBSOLETE | — | OBSOLETE |
| SEALED | — | SEALED |

\* A 的 Revised 表示改版阶段，映射为 phase/revision，不一定是 CHANGING。

---

## 9. 安全与权限（合并角色）

保留 B 的权限码模型，扩展角色覆盖 A 七级语义：

| 角色码 | 含义 | 主要权限 |
|--------|------|----------|
| ADMIN | 系统管理员 | 全部 |
| ENGINEER / Rd | 研发 | part/bom/ecn/3d/ai 写 |
| RD_LEAD | 研发主管 | ECN L1、发布 |
| SCM_LEAD | 供应链总监 | ECN L2 |
| PROCESS | 工艺 | sop/dfm/fixture |
| QA | 品质 | inspect、只读结构 |
| PURCHASE | 采购 | 外协申请、价格只读策略 |
| WAREHOUSE | 仓库 | MBOM 只读、库存码 |
| TRADE | 外贸 | trade/packing/sbom |
| SERVICE | 售后 | sbom/手册 |
| SUPPLIER | 外协 | 仅审批包 |
| CUSTOMER | 客户 | 分享预览 |
| SALES | 销售 | 外观 3D + 成品 |

**修复 B：** ECN L2/effect 权限种子补齐；void 加鉴权。

---

## 10. 分阶段实施路线

### Phase 0 — 止血（1 周）【在 PLM-2】

1. 修复 ECN effect 版本落库 + part_version 快照  
2. BOM archiveVersion 落库；item.path 写入  
3. 统一 `lifecycle_state` 字段（迁移脚本）  
4. changeStatus 加转换守卫  

### Phase 1 — 主数据与产品档案合并（2 周）

1. `plm_part` / `plm_part_param`  
2. 迁入 A 前端 ProductArchive + 导入  
3. `/products` API 与 `/material` 兼容层  
4. 档案树挂到 part 创建  

### Phase 2 — BOM 三态 + 3D 任务（3 周）

1. bom_type EBOM/MBOM/SBOM  
2. AlgorithmClient：explode/extract  
3. 迁入 Web3D / Templates / Jobs 页面  
4. where-used / BOM compare  

### Phase 3 — 变更闭环增强（2 周）

1. ECR + impact 模型  
2. ECN compare/refresh/freeze 并入审批流  
3. effect 管线按 change_type  
4. 发布门禁  

### Phase 4 — 工艺/外贸/AI/装箱（3 周）

1. process/trade/packing/ai 后端服务 + 页面迁入  
2. 事件驱动：ECN 生效 → 文档刷新任务  
3. Redis 队列落地  

### Phase 5 — 多端与集成（2 周）

1. 销售/车间/客户 portal 裁剪  
2. ERP/MES 适配器真联调  
3. 性能、审计、备份、压测  

---

## 11. 关键缺陷闭环对照

| 原问题 | V5 对策 |
|--------|---------|
| 无统一生命周期 | lifecycle 引擎 + 转换表 |
| 双状态字段 | 单一 lifecycle_state + 正交 phase |
| 无项目阶段 | phase 字段 |
| 无 ECR | plm_ecr |
| 变更影响未落地 | plm_ecn_impact + effect 管线 |
| 版本表空转 | 强制 snapshot 写入 |
| ltree 未用 | 写入 + where-used |
| 发布无门禁 | release_gate |
| ECN 版本不落库 | 事务内 update + 单测验 DB |
| 文件仅打标 | obsolete + 水印任务 |
| 两套前端分裂 | 单前端信息架构 |
| Redis 未用 | 队列/锁/缓存 |
| A 无企业安全 | 沿用 B RBAC/水印/内外网 |
| B 无三态 BOM/外贸/AI | 迁入 A 能力包 |

---

## 12. 仓库落地建议

推荐 **单一仓库演进 PLM-2**（当前工作区）：

```
PLM-2/
├── docs/
│   └── merged-architecture-v5.md    # 本文档
├── database/
│   ├── 01_schema.sql                # 演进
│   ├── 10_v5_part_lifecycle.sql     # 新迁移
│   └── 11_v5_bom_tri_state.sql
├── plm-backend/                     # 扩展包
├── plm-frontend/                    # 迁入 A views
│   └── src/views/
│       ├── products/                # 自 A ProductArchive
│       ├── process/ trade/ ai/ packing/ jobs/ templates/
├── plm-algorithm/                   # 兼容 explode/job API
├── templates/led-templates/         # 自 A 拷贝
└── docker-compose.yml
```

A 仓库 `D:\PLM` 冻结为「能力来源与对照实现」，不再双轨开发。

---

## 13. 成功标准（验收）

1. `/products` 可完成建档→参数→3D 任务→EBOM→MBOM/SBOM→发布。  
2. ECN：对比标红 → 双级审批 → 生效后 **DB 中 version_no 正确、快照可查、旧文件不可下载**。  
3. 任意非法状态跳转被 API 拒绝并留审计。  
4. 外协水印下载与客户分享在统一权限下可用。  
5. 模具/品质/外贸/装箱数据均挂同一 `part_no`。  
6. 一套 docker-compose 启动完整链路；不再依赖 :3000 mock 后端。  

---

## 14. 附录：能力来源速查

| V5 模块 | 主来源 | 次来源 |
|---------|--------|--------|
| 产品档案库 UI/导入 | A | — |
| 零件参数 | A | — |
| 物料锁定/编号规则 | B | — |
| BOM 树/环检测 | B | A 层级 |
| BOM 三态/ERP 导出 | A | — |
| ECN 审批/日志 | B | — |
| ECN diff/刷新/冻结 | A | — |
| 档案树/文件/水印/分享 | B | — |
| 外协 | B | — |
| 模具全生命周期 | B | — |
| SOP/DFM/工装/三检 | A | B quality |
| 外贸/装箱/AI/模板/任务 | A | — |
| Web3D 爆炸 | A | B Model3DViewer |
| RBAC/审计/PG | B | A 角色语义 |
| 生命周期引擎 | **新设计** | — |

---

*本文档为合并实施的架构基线；开发以 Phase 0→5 为序，先修生命周期正确性，再迁入 A 的体验与算法能力。*
