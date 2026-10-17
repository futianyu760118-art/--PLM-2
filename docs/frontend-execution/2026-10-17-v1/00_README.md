# AEOS 灯具研发系统前端改造执行资料包 V1.0

编制日期：2026-10-17（Asia/Shanghai）  
试点企业：宁波恒剑光电科技有限公司  
目标仓库：https://github.com/futianyu760118-art/--PLM-2.git  
性质：开发交接与设计基线；本目录归档于指定GitHub仓库；未实施正式前端改造，未完成生产联调或最终验收。

## 本次目标
在现有 PLM 后端不变的前提下，在原 Vue 前端实现五种可切换呈现方式，并覆盖从立项到产品退市的业务展示、使用说明、学习教材和个性化外观。

## 阅读与执行顺序
1. 阅读 01 范围与决策，确认哪些是现有能力、哪些只是规划。
2. 打开 demo/AEOS_PLM_Background_Themes_V6_2.html，用 Edge/Chrome 体验。
3. 根据 02、03 整理页面、路由与接口映射，进入原前端工程组件化实施。
4. 按 04 任务清单依赖推进，在表中填写实际负责人和证据路径。
5. 按 05 逐项验收。浏览器测试失败或未执行的项目不得填“通过”。
6. 按 06 技能执行记录与 07 教材安排评审、培训和交接。

## 文件目录
- 01_Scope_and_Decisions.md：范围、边界、已确定设计。
- 02_UX_and_Lifecycle.md：五模式、侧栏、完整生命周期、配色与适配。
- 03_Frontend_Implementation.md：组件、接口、路由、状态、存储与迁移。
- 04_Execution_Tasks.csv：可分派任务、依赖、交付物、验收条件。
- 05_Acceptance_and_QA.md：功能、权限、无障碍、浏览器、回退验收。
- 06_Skills_and_Evidence.md：Skill 使用情况、证据和未完成项目。
- 07_User_Guide_and_Training.md：操作说明及培训执行。
- 08_Risks_and_Handover.md：风险、交接与发布检查。
- demo/：V6.2 单文件原型；不是正式 Vue 前端替换包。
- qa/：脚本检查文件（若可获得）与校验说明。
- MANIFEST.json：资料文件大小及 SHA-256，便于交付核对。

## 打开方式
解压到普通文件夹，双击 demo 中 HTML 文件。若出现源码预览，选择“打开方式 → Microsoft Edge / Google Chrome”。无需安装依赖、无需登录或启动后端。

## 状态声明
界面、任务完成、配色、阅读标记等为本地示例行为。数据库、真实权限、发布审批、退市执行、Multica Agent 运行均未通过本 DEMO 接入。Chromium 下载失败导致真实浏览器验收尚未完成。
