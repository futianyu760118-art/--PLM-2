# 技术债登记（TECH_DEBT）

| 项 | 内容 |
|---|---|
| 依据 | [ADR-2026-001](../adr/ADR-2026-001-3d2d-capability-package-boundary.md)（3D-2D 定位为第三能力包，唯一事实 Owner 收归 PLM-2） |
| 来源 | PAND-44《整改方案》第 4 节 TD-1~TD-6 |
| 范围 | 3D-2D 能力包并入 PLM-2 底座过程中的遗留依赖与临时实现 |
| 登记日 | 2026-09-28（Phase 0 生效日） |

## 到期日口径

PAND-44 以「Phase N 完成前」表述到期日。Phase 时长取 `docs/merged-architecture-v5.md:498-536`
（Phase 0–5 = 1/2/3/2/3/2 周），自 Phase 0 生效日 **2026-09-28** 起顺排：

| Phase | 区间 | 完成日 |
|---|---|---|
| Phase 0 止血 | 2026-09-28 → 2026-10-05 | 2026-10-05 |
| Phase 1 主数据 | 2026-10-05 → 2026-10-19 | 2026-10-19 |
| Phase 2 3D/出图任务 | 2026-10-19 → 2026-11-09 | 2026-11-09 |
| Phase 3 变更闭环 | 2026-11-09 → 2026-11-23 | 2026-11-23 |
| Phase 4 事件驱动 | 2026-11-23 → 2026-12-14 | 2026-12-14 |
| Phase 5 集成与证据 | 2026-12-14 → 2026-12-28 | 2026-12-28 |

排期变化时到期日随各 Phase 完成日顺延，须在本表更新并留痕。

## 债务清单

| 编号 | 技术债 | 证据（路径:行号） | Owner | 到期日 | 处置 | 状态 |
|---|---|---|---|---|---|---|
| TD-1 | 3D 解析 / GLB 重复实现：3D-2D 自建第二步 STEP 解析与 GLB 轻量化，与 PLM-2 `model3d` 重复 | `--3D-2D/backend/app/services/step_parser.py:96-147`、`--3D-2D/backend/app/routers/products.py:441-506` | M04 研发自治中心 | 2026-11-09（Phase 2 完成前） | 删除重复实现，改调 PLM-2 `plm-algorithm/app/api/model3d.py` 的 `/model3d/parse`、`/simplify` | 未处置 |
| TD-2 | 3D-2D 自建 `parts`/`drawings` 第二事实源，与 PLM-2 主数据重复 | `--3D-2D/db/schema.sql:37-103`、`--3D-2D/backend/app/models/part.py:27-75`、`--3D-2D/backend/app/models/drawing.py:8-43` | M04 研发自治中心 | 2026-10-19（Phase 1 完成前） | 停写本地事实表并迁移到 PLM-2 主数据，提供 `/products` 兼容层 | **Phase 0 部分处置**：新增业务写入已冻结（`--3D-2D/backend/app/core/fact_source.py`），两表已标 deprecated（`db/schema.sql:37,70`），存量迁移待 Phase 1 |
| TD-3 | 对外集成仅有模拟 stub，非受控 Facade | `--3D-2D/backend/app/routers/archive.py:137-170` | M04 研发自治中心 | 2026-12-28（Phase 5 ERP/MES 真联调前） | 替换为受控 Facade（鉴权、幂等、错误码、审计、`source_system/correlation_id/evidence_refs`） | 未处置 |
| TD-4 | `os.unlink` 物理删除业务档案，违反可追溯红线（原三处：零件删除、成品删除、归档图删除） | `--3D-2D/backend/app/routers/parts.py:120-162`、`--3D-2D/backend/app/routers/products.py:140-196`、`--3D-2D/backend/app/routers/archive.py:83-98` | M04 研发自治中心 | 2026-10-05（Phase 0 完成前） | 改为「作废 + 水印 + 封存」，档案全程留痕可追溯 | **已处置（Phase 0，2026-09-28）**：三处物理删除改为作废标记 + 水印 + 封存，统一走 `--3D-2D/backend/app/core/retention.py`；作废字段见 `--3D-2D/db/schema.sql:37-103` 的 `voided/voided_at/voided_by/void_reason/sealed_path` |
| TD-5 | 本地版本机制替代统一生命周期：`version_no`/`is_latest`/`revert` 与就地升版 | `--3D-2D/backend/app/routers/versions.py:29-45`、`--3D-2D/backend/app/routers/conversion.py:33-41` | M04 研发自治中心 | 2026-11-23（Phase 3 完成前） | 接入 PLM-2 `LifecycleServiceImpl` + `EcnServiceImpl`，删除本地状态机与降级机制 | 未处置 |
| TD-6 | 无 CI 与 G0–G6 门禁 / Evidence 证据 | 两仓库缺 `.github/workflows`；对照 `AEOS-/ledger/DEVELOPMENT_LEDGER_V1.md:20-26,32` | M12 DevSecOps（主）+ M04 研发自治中心 | 2026-12-28（Phase 5 完成前） | 补 CI（构建 + 测试）与门禁证据链：Automated Test + Evidence + Owner Verified | 未处置 |

## 处置规则

1. 到期未处置的债务须在本表登记延期原因与新的到期日，并向架构板报备。
2. 债务关闭须附证据（PR / 提交 / 测试结果），仅「已改代码」不构成关闭。
3. 新增债务沿用 `TD-<序号>` 编号，登记 Owner + 到期日 + 处置，不得无主挂账。
