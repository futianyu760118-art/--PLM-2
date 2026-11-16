# AEOS M04 研发岗位微系统 × PLM 嵌入方案 V0.1
状态：L0 前端模拟，G0/G2/G3 未通过。日期：2026-10-23。
## 使用
PLM Vue 3 + Element Plus 前端中新增路由 `/project/poms`；既有 /project/list、/project/worksheets、/bom、/ecn、/ledger、/agent 保持不变。通过原有登录、布局和路由体系访问。
## 四类能力边界
- M04 PLM：研发对象、需求、图纸、BOM、样品、ECN、技术转移、专业判断与 Result。
- M10 POMS：岗位装配、未来待办、过去结果、采集 UI 和个人智能体入口。
- M01：人员岗位授权、DEH Action/Verify/Guard。没有权限不得依赖前端隐藏进行控制。
- M02：统一 Evidence/Event/Trace；Hub：真实 Agent Runtime。M11：网关。
## 开发演示
目前包含研发工程师、研发负责人、样品验证员三套岗位任务；恒剑灯具和贞观定时器两种专用参数模型；完成声明、记录提交、Agent 打开、研发九阶段跳转。所有任务和数据均只在浏览器当前页面内存中；刷新丢失。
## 真实对接工作包
1. M01 统一登录身份/角色/授权与动态装配 manifest，禁用本地 role 模拟。
2. M04 从已有 PLM 工单/项目节点/研发工作表 API 获取真实待办，并按 object_ref 路由进入专业界面。
3. POMS 采集提交经 PLM Domain API，形成专业 Result，然后经 M02 Evidence 审核；done 不等于 verified。
4. 将硬编码灯具/定时器字段迁移至签批的 Capability Pack JSON Schema。
5. 将 Hub Agent 清单/Session/Run/Guard 经受控 BFF 适配，不在前端保留 Runtime 凭证。
6. M12 测试：岗位撤权、跨企业隔离、能力包切换、幂等、Evidence 溯源、功能回归、手机响应。
## 验收
L0：切换岗位/产品，任务和字段变化；PLM 原有菜单仍可用；浏览器无构建错误。
G2/G3 正式验收尚需真实 API、身份、审计、接口契约和 UAT。独立 HTML Demo 可用于现场评审，不能代替部署验证。
