# AEOS 研发自治中心 V0.2 × PLM-2 V1.1 实施基线

> 日期：2026-09-23  
> 分支：`aeos-rd-v0.2-plm2-v1.1`  
> 依据：AEOS研发自治中心数据模型V0.2、PLM-2改善方案V1.1、AEOS开发执行手册V1.0、Data Foundation V0.2

## 1. 定位

PLM-2 不被替换，而被正式定位为 **R&D Autonomy Center 的 Engineering Kernel**。

- AEOS Control Plane：目标、权限、Policy、Decision、Evaluation、Learning。
- Data Foundation：Entity / Metric / Data Product / Evidence / Permission / Audit。
- R&D Action Center：研发事项入口、路由、执行、独立验收与Result回写。
- PLM-2 Engineering Kernel：Project / Part / BOM / ECN / Mold / Test / Trial / File / Archive / Process。

## 2. 本轮优先落地

本分支先落地归档中最高优先级、且不依赖尚未冻结业务阈值的能力：

1. 19节点保持不变，7个关键节点增加 RD_LEAD → GM 双级审批。
2. 关键节点不得通过直接修改 status 绕过审批。
3. Health Score 的 CF 改为“正式批准项目变更”，不再使用 node.edit_count。
4. CR 改为项目节点必填字段完整率，不再等同 plan_date 覆盖率。
5. 建立 Gate Namespace，区分 DEV-G0~G6、RD-MG1~MG5、PLM-G0~G8。
6. 建立 AEOS Evidence Registry；节点Evidence获得统一 Evidence_ID。
7. 17层档案采用 FileObject + ArchiveReference 的引用模型。
8. 扩展 Metric Object 治理字段，所有阈值默认 PROPOSED。
9. 建立 R&D Action Center 数据底座。
10. 新增关键审批与Action权限能力，但不擅自创建“研发主管/总经理”新角色。

## 3. 关键节点

真实节点来自当前代码 `ProjectProgressService.NODES`：

`PLAN, BOM, SPEC, CONFIG, MOLD_DRAWING, MOLD_REVIEW, HAND_SAMPLE, MOLD, MOLD_SAMPLE, PACKAGING, ELEC_TRIAL, RD_TRIAL, ENG_TRIAL, PROD_TRIAL, TEST_REPORT, TECH_TRANSFER, SHIPMENT, REVIEW, OTHER`

关键节点固定为：

- MOLD_REVIEW
- MOLD
- RD_TRIAL
- ENG_TRIAL
- PROD_TRIAL
- TEST_REPORT
- TECH_TRANSFER

关键节点完成条件：

```
actual_date != null
AND evidence_count >= 1
AND RD_LEAD = APPROVED
AND GM = APPROVED
```

## 4. Gate命名约束

- `DEV-G0~DEV-G6`：AEOS开发项目治理门。
- `RD-MG1~RD-MG5`：研发业务原型Gate，目前为DRAFT。
- `PLM-G0~G8`：工程Gate，正式名称、边界和Node映射仍是G2待冻结项。

任何 `DRAFT/PENDING/PROPOSED` 规则不得被当作生产自动放行规则。

## 5. Health Score V0.2

公式保持：

`HS = 100 × (0.35·NCR + 0.25·KCR + 0.20·OTR + 0.10·CR + 0.10·CF)`

但语义修正为：

- NCR：节点完成率。
- KCR：关键节点完成率。
- OTR：有计划日期的已完成节点中准时完成率。
- CR：项目节点必填字段完整率。
- CF：`max(0, 1 - 正式批准变更数 / 10)`。

禁止再用节点编辑次数代替工程/项目变更次数。

## 6. Evidence

节点上传文件或系统内填写Evidence时，必须同时生成统一 `Evidence_ID` 并登记到 `aeos_evidence`。

Evidence 只能被撤销/失效，不应通过复制文件形成第二真相源。

## 7. Action Center 状态机

目标状态机：

`NEW → CLASSIFIED → ASSIGNED → IN_PROGRESS → WAIT_ACCEPTANCE → ACCEPTED → CLOSED`

旁路：

`BLOCKED / REJECTED / CANCELLED`

硬规则：

- CLASSIFIED 后必须绑定真实 `Object_Type/Object_ID`。
- 无 Evidence 不得提交验收。
- 执行人与验收人必须分离。
- CLOSED 前必须存在 Result。
- Agent V0.1 只开放 L0-L2。

本轮只先落库数据模型；业务API在后续WP13实现。

## 8. G2尚待冻结

以下内容不能在本PR中擅自视为正式制度：

1. PLM-G0~G8 正式名称及其与19 Node / RD-MG1~MG5的映射。
2. TEST_FIRST_PASS、MOLD_T0_PASS、TRIAL_FPY等真实基线和阈值。
3. “研发主管/总经理”在现有六角色RBAC中的最终映射。
4. 发布、回滚、恢复演练的真实环境参数。

## 9. 验收

本轮至少应验证：

- 普通节点满足 actual_date + Evidence 后可 DONE。
- 关键节点无双级审批不得 DONE。
- RD_LEAD 批准后进入等待GM审批。
- GM批准后关键节点 DONE。
- 驳回回到 IN_PROGRESS。
- Health Score 的 CF 与正式变更数有关，与 edit_count 无关。
- 节点Evidence生成统一 Evidence_ID。
- 审批接口存在权限隔离和操作日志。

后续继续按 A-track + D-track + AT-track 三轨验收推进。
