# PLM-2 整体测试报告（遵循 AEOS 原则）

> 文档编号: HJ-PLM-AEOS-OVERALL-TEST-v1.0
> 依据: AEOS(`https://gitee.com/ningbohengjianguangdian/AEOS.git`) — Contract Pack / DEH Contract / System Registry / Development Ledger(G0–G6)
> 测试脚本: `scripts/test-aeos-overall.ps1`
> 环境: PLM-2 运行实例 `http://localhost/`（Docker: plm2-frontend/backend/postgres/redis/algorithm）
> 结论: **PASS 36 / FAIL 0**

---

## 一、测试范围

1. **模块接口可用性（Schema/API 契约）**：31 个模块/接口
2. **AEOS 专项**：对象契约（Object Contract）、幂等（Idempotency）、权限强制（Permission）、事件与证据（Event/Evidence/Audit）、对象引用（object_ref）

## 二、结果明细

### 2.1 模块接口（均 HTTP200 / code=200）

M01 认证、M04 项目、M04 立项(list/stats)、M04 项目跟踪、M04 工作表、M04 节点↔工作表对照、M04 变更列表、M02 基础数据、同步中枢、M04 物料、M04 BOM、M04 ECN、M04 模具、M08 品质、M04 工序、M04 3D、档案、外协、分享、数据质量 DQ、度量 Metric、KPI、OKR、分析 Analytics、改善 Issue、待办 WorkItem、智能体 Agent、通用查询 Query、集成 Integration、系统自检。

### 2.2 AEOS 专项

| 检查项 | 对应 AEOS | 结果 | 证据 |
|---|---|---|---|
| 登录获取 JWT | M01 Kernel Identity | ✅ | code=200 |
| 无 token 拒绝 | 权限强制 | ✅ | code=401（HTTP200，业务码） |
| Object Contract 信封 | CONTRACT_PACK §1 / DEH §1 | ✅ | 工作表记录 `tenant_id=hjgd, source_system=PLM2, revision=1` |
| 对象引用 object_ref | DEH §3/§7 | ✅ | 工作表自动引用 `project_id=298`（PROJECT） |
| 入站幂等 | DEH §9 (idempotency_key) | ✅ | 同 `event_id` 第二次 `duplicated=true` |
| 事件 + 变更留痕 | Event/Evidence Contract | ✅ | 工作表变更写入变更列表（changes 43→44） |

## 三、AEOS 门禁对照

| 门禁 | 内容 | 状态 |
|---|---|---|
| G0 架构门禁 | 边界、Object/Event 契约 | 满足（M04 域内契约就绪） |
| G1 模块化门禁 | 无新增跨域直写（跨域走 Contract/Event） | 满足（同步走 `/v1/sync/inbound` + Outbox） |
| G3 闭环门禁 | Fact→Event→…→Evidence 可追溯（域内） | 满足（工作表→事件→节点→跟踪→变更列表） |
| DONE 标准 | 部署 + 自动化测试 + 证据 | 本报告为测试证据（Owner Verify 待业务方） |

## 四、说明与后续

- 权限拒绝以**业务码 401**（HTTP 200）返回，前端按 `res.code` 处理；符合现有契约。
- 本次为**接口冒烟 + AEOS 契约专项**；未覆盖：压力/并发、完整 UAT 业务剧本、真实 ERP/MES 联调、扫描件 PDF 导入(需 OCR)。
- 建议下一步：补充关键业务流程的 UAT 剧本（立项→审批→转项目→BOM→ECN→技转）与 Evidence 归档。

---

## 五、深度测试（功能可用性）结果

| 套件 | 命令 | 结果 |
|---|---|---|
| 后端单元/集成 | `mvn -o test`（22 个测试类） | **149 / 0 失败** |
| 前端 | `npx vitest run` | **8 / 8** |
| 算法服务 | `pytest tests/` | **17 / 17** |
| AEOS 整体接口 | `scripts/test-aeos-overall.ps1` | **36 / 0** |
| 关键业务流 E2E | `scripts/test-aeos-functions.ps1` | **16 / 0** |

业务流覆盖：基础数据 CRUD、物料 建档→提交评审→发布→删除、工作表→完成判定→节点（闭环）、立项五阶段审批推进、自动编号、分享、系统自检。

## 六、深度测试发现并修复的缺陷

| # | 缺陷 | 影响 | 修复 |
|---|------|------|------|
| 1 | `plm_code_issue_log` 缺列 `rule_code`（代码写 `rule_code`，表为 `rule_id`） | **自动编号不可用**（物料建档 500，事务中止） | `database/33_code_issue_rule_code.sql` 补列 |
| 2 | 逻辑删除列 `deleted` 无默认值且实体未赋值 → 插入为 NULL | 物料等**新增记录不可见**（查询/动作 404、列表为 0） | `database/34_fix_deleted_default.sql` + `BaseEntity.deleted=0` |
| 3 | 物料重复导入触发唯一约束 → 显示"系统异常" | 重复导入 500 | 全局异常处理器友好化 + dataio 逐行降级 + 导入重复归类为"跳过" |
| 4 | 测试用例过时（19 节点、缺 ChangeLogService mock） | 后端测试红 | 更新 `ProjectProgressServiceTest` |

## 七、结论

- 所有自动化测试与端到端功能测试**全部通过**；核心业务闭环（物料/立项/进度/变更）**可用**。
- G0(契约)/G1(无跨域直写)/G3(域内闭环可追溯) 满足；本报告为测试证据（Evidence）。

---

*本报告为 AEOS 要求的测试证据（Evidence）；Owner Verify 由业务负责人确认。*
