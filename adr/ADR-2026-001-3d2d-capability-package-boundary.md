# ADR-2026-001 — 3D-2D 定位为「第三能力包」，唯一事实 Owner 收归 PLM-2

> 状态：Accepted（Phase 0 生效）  
> 关联：PAND-43《符合性检查报告 + 差异清单》、PAND-44《整改方案》、PAND-101《治理 Phase 0 ADR 裁定》

## Context

1. AEOS 一级标准要求「先边界、后功能；先闭环、后扩展」，模块边界清晰、职责唯一，不再产生重复建设（`AEOS-/architecture/SYSTEM_REGISTRY_V1.md:8`、`AEOS-/README.md:26`），并规定 Product/Drawing/BOM/Release 的唯一事实 Owner 为 M04 R&D（`AEOS-/architecture/DATA_OWNERSHIP_MATRIX_V1.md:9`）。影响一级边界/Data Owner/核心 Contract 的决定必须建 ADR（`AEOS-/adr/README.md:3`）。

2. 现状核查（真实 checkout，非臆测）：
   - PLM-2 底座（实机仓库 `PLM2`，gitee `ningbohengjianguangdian/PLM2.git`，即 PAND-43/44 所称 `--PLM-2` 的同源代码）：持有唯一料号主数据 `plm_material`（`plm-backend/src/main/java/com/hjgd/plm/material/entity/Material.java:15-52`，含 `drawingNo`/`drawingRevision` :48-51）、统一生命周期状态机（`lifecycle/service/impl/LifecycleServiceImpl.java:32-88`）、ECN 变更闭环（`ecn/service/impl/EcnServiceImpl.java:221-290`，生效时发 `ecn.effective` :289）、自有 3D 解析/GLB（`plm-algorithm/app/api/model3d.py:17-34,37-61,69-103`）与模具 DWG/DXF→8 类图纸分解（`plm-algorithm/app/api/drawing.py:13-38`）、档案禁物理删除承诺（`README.md:163`）。
   - 3D-2D 能力包（实机仓库 `--3D-2D`，github）：自建 `parts`/`drawings` 第二事实源（`--3D-2D/db/schema.sql:37-58,61-84`、`backend/app/models/part.py:21-47`、`backend/app/models/drawing.py:8-37`）；集成仅有模拟 stub（`backend/app/routers/archive.py:133-167`）；本地 `version_no/is_latest/revert` + 就地升版替代生命周期（`backend/app/routers/versions.py:29-45`、`conversion.py:32-41`）；重复实现 STEP 解析/GLB（`backend/app/services/step_parser.py:101-152`、`backend/app/routers/products.py:399-508`）；多处 `os.unlink` 物理删除（`backend/app/routers/parts.py:110-130`、`products.py:128-159`、`archive.py:81-93`）。其唯一差异能力为「STEP→三视图/装配/检验图 SVG 出图」（`backend/app/routers/conversion.py:43-95`、`products.py:192-388`）。

3. 关键事实澄清：合并基线 `docs/merged-architecture-v5.md:4,12-21` 的「A 系统」是 `D:\PLM`（:3000，三维装配爆炸 + 外贸/AI 全链路演示），**并非** `--3D-2D` 仓库（:8000，灯具零件 STEP→2D 出图）。`--3D-2D` 是第三套、此前未纳入任何基线（PAND-43 差异项 8）。结论是「底座 + 能力包」模式尚未成立：两系统互不调用、事实重复、零契约（PAND-43 总览）。

## Decision

1. **定位**：正式把 `--3D-2D` 定位为**第三能力包（出图能力包）**，经契约并入 PLM-2（M04 研发自治中心底座）；它既不是基线所指的 A 系统 `D:\PLM`，也不是 B 系统 `D:\PLM-2`。
2. **唯一事实 Owner 收归 PLM-2（M04 R&D）**：Part / Drawing / BOM / Release 的唯一主数据与正式事实源由 PLM-2 持有（对齐 `DATA_OWNERSHIP_MATRIX_V1.md:9`）；3D-2D 不得自建或写入第二事实源，跨域访问走 Contract/Facade，禁直连写表（`SYSTEM_REGISTRY_V1.md:26`）。
3. **保留唯一差异能力**：3D-2D 仅保留「STEP→三视图/装配/检验图 SVG 出图」；其 STEP 解析/GLB 转换与 PLM-2 自有 `model3d`/`drawing` 重复，**正式实现裁定为 PLM-2 `model3d`**，3D-2D 的重复实现（`step_parser.py`、`products.py:399-508`）停用/删除。
4. **生命周期与变更闭环**：3D-2D 图纸归档/发布/变更接入 PLM-2 统一生命周期引擎 + ECN 闭环，删除本地 `version_no/is_latest/revert`/就地升版机制。
5. **可追溯红线**：3D-2D 禁 `os.unlink` 物理删除，改作废/水印/封存（对齐 `PLM2/README.md:163`）。
6. **合作契约**：两系统间 API/Event/Result/Evidence 契约版本化（`CONTRACT_PACK_V1.md:17,21,24,29`）；`archive.py:133-167` 模拟 stub 替换为受控 Facade（幂等、错误码、审计、`source_system/correlation_id/evidence_refs`）。

### Phase 0 冻结决策（立即生效）

- **冻结第二事实源写入**：停写 3D-2D 自建 `parts`/`drawings` 表（`--3D-2D/db/schema.sql:37-84`），标记 deprecated；料号/图号唯一来自 PLM-2。
- **禁物理删除**：`parts.py:110-130`、`products.py:128-159`、`archive.py:81-93` 三处 `os.unlink` 清零，改作废/水印/封存。
- **登记技术债**：在 PLM-2 建 `ledger/TECH_DEBT.md`，登记 TD-1~TD-6（3D 解析/GLB 重复、第二事实源、模拟 stub、物理删除、本地生命周期、无 CI/Gate 证据），每条含 Owner + 到期日。

## Alternatives

- **A. 维持现状（两系统各自闭环）**：被否。违反 M00 不重复建设与单一 Owner 红线，事实源重复、无契约、无闭环。
- **B. 3D-2D 为第一实现、PLM-2 让位**：被否。PLM-2 已具备 RBAC/事务/审计/统一生命周期/ECN 底座与唯一料号主数据；倒挂会引入第二事实源与双状态机。
- **C. 一次性弃用 3D-2D、出图算法直接迁入 PLM-2**：作为最终态可选，但一次性大迁移风险高、无兼容期。
- **选定 D（= Decision）**：3D-2D 作为出图能力包经契约并入 PLM-2，保留差异能力、收归事实 Owner、分 Phase 0–5 渐进并入。

## Consequences

- 正向：单一事实源、无重复 3D/GLB 算法、全链可追溯、契约化协作，满足 AEOS「先边界、后功能」。
- 代价/风险：3D-2D 需停用本地 `parts`/`drawings` 表并做迁移（Phase 1 提供 `/products` 兼容层）；出图链路前置解析依赖 PLM-2 `model3d`（增加一次跨服务调用）；旧数据（3D-2D 自建 part/drawing）需映射到 PLM-2 主键（`part_no`/`drawingNo`），迁移期需一致性校验。
- 门禁：后续能力并入按 `merged-architecture-v5.md:496-536` 的 Phase 0–5 落地；交付须满足 DONE 定义（`AEOS-/ledger/DEVELOPMENT_LEDGER_V1.md:32`）。

## Owner

- **唯一事实 Owner + 底座**：PLM-2（M04 研发自治中心）。
- **边界/契约裁定**：架构板（Architecture Board）。
- **落地实施**：M04（研发自治中心）负责 Phase 0–5 实施；M12（DevSecOps）补 CI/Gate/Evidence。

## Effective Date

2026-09-28（Phase 0 止血阶段生效；3D-2D 事实源冻结与物理删除禁令即刻生效）。

## Rollback

- 本 ADR 以裁定边界与 Owner 为主，可逆。若 Phase 0 冻结导致 3D-2D 出图链路不可用，可恢复本地 `parts`/`drawings` **只读**，但不得恢复第二事实源写入；正式回滚须由架构板再裁定并登记新 ADR。

## Evidence

- 基线「A 系统」指向：`docs/merged-architecture-v5.md:4,12-21`
- PLM-2 唯一主数据/生命周期/ECN：`plm-backend/src/main/java/com/hjgd/plm/material/entity/Material.java:15-52`、`lifecycle/service/impl/LifecycleServiceImpl.java:32-88`、`ecn/service/impl/EcnServiceImpl.java:221-290`；档案禁物理删除 `README.md:163`
- PLM-2 自有 3D/图纸能力：`plm-algorithm/app/api/model3d.py:17-34,37-61,69-103`、`plm-algorithm/app/api/drawing.py:13-38`
- 3D-2D 第二事实源：`--3D-2D/db/schema.sql:37-58,61-84`、`--3D-2D/backend/app/models/part.py:21-47`、`--3D-2D/backend/app/models/drawing.py:8-37`
- 3D-2D 零契约 stub / 生命周期 / 物理删除：`--3D-2D/backend/app/routers/archive.py:133-167`、`versions.py:29-45`、`conversion.py:32-41`、`parts.py:110-130`、`products.py:128-159`、`archive.py:81-93`
- 3D-2D 重复 3D 解析/GLB：`--3D-2D/backend/app/services/step_parser.py:101-152`、`--3D-2D/backend/app/routers/products.py:399-508`
- 3D-2D 唯一差异能力：`--3D-2D/backend/app/routers/conversion.py:43-95`、`--3D-2D/backend/app/routers/products.py:192-388`
- 标准出处：`AEOS-/architecture/DATA_OWNERSHIP_MATRIX_V1.md:9`、`SYSTEM_REGISTRY_V1.md:8,26,30`、`contracts/CONTRACT_PACK_V1.md:17,21,24,29`、`ledger/DEVELOPMENT_LEDGER_V1.md:20-26,32`、`adr/README.md:9`
