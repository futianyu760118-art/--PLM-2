# 03 前端实施设计

## 仓库确认与冻结
开始前记录默认分支 HEAD、前端依赖锁文件、原页面截图、API清单和当前构建结果。此前源码评估读取于2026-10-16/17，未冻结整个仓库到一个明确 SHA，不应当作最新全仓完整审计。

## 推荐组件分层（拟新增，不表示已存在）
- layouts：ClassicLayout、ProductLayout、ProfessionalLayout、CollaborationLayout、MulticaInspiredLayout。
- navigation：模式配置、项目上下文、统一图标、侧栏与窄屏入口。
- shared：ProjectHeader、WorkItemPanel、EvidencePanel、StageGatePanel、ModelViewerAdapter。
- views：项目、产品概览、专业视图、生命周期、说明、学习中心、外观设置。
- stores：业务实体归一化、当前上下文、外观与阅读进度。
- adapters：只转换已有返回字段、分页和日期，不补造业务状态。

保留 src/utils/request.js 的 /api 基址、Bearer令牌、Result处理和登录失效处理。业务表单与提交方法只维护一份。

## 接口矩阵（源码存在，运行待联调）
| 页面 | 已读取接口 | 实施要求 |
|---|---|---|
| 项目列表/详情 | GET /api/v1/projects；GET /api/v1/projects/{id} | 使用真实id，不以示例编号替换 |
| 阶段门记录 | GET /api/v1/projects/{id}/gates | 定义从后端取得 |
| 阶段门操作 | POST /api/v1/projects/{id}/gates/{gateCode}/pass 或 fail | 不允许前端单独决定放行 |
| 我的待办 | GET /api/v1/work-items/my | 数据范围与身份由服务端确认 |
| 完成待办 | POST /api/v1/work-items/{id}/complete | DONE不等于阶段门通过；工序可能联动 |
| BOM | GET /api/bom/page；GET /api/bom/{id}/tree | 复用旧BOM逻辑 |
| 料号引用 | GET /api/bom/where-used/{partNo} | 不从局部列表推断全库引用数 |
| 研发工作表 | GET /api/v1/rd/sheets/{type} | type使用现有元数据，不自行发明 |
| 档案树 | GET /api/archive/tree/{partNo} | 复用附件鉴权与下载路径 |
| 资料汇总 | GET /api/ledger/repository | 保留分类筛选序列化口径 |
| 模型预览 | GET /api/model3d/{id}/preview | 有效GLB/glTF才能预览 |
| 规则工具 | POST /api/v1/agents/{agentCode}/sessions；POST /api/v1/agents/sessions/{id}/messages | 保留规则工具标签；无LLM时不伪装生成式能力 |

不得只因前端封装存在就判定接口可用。每行补充请求示例、返回样本、权限账号和联调结果。

## 状态与路由
- WorkItem保留 OPEN、ESCALATED、DONE；Multica参考模式不能写入其独立状态枚举。
- 项目currentGate与工作项status分离；生命周期仅表达业务导航，不重新定义后端阶段状态。
- 建议URL保存 mode、projectId、view/tab；校验白名单与项目访问权限。
- 模式偏好、主题、阅读标记、草稿分别存储；示例重置不清除学习与主题，除非用户选择对应重置。
- localStorage不保存令牌之外新增敏感业务内容；正式草稿内容范围与保存期限应明确，当前原型不含真实业务数据。
- 不同本地HTML文件/浏览器之间存储共享行为不可依赖，演示状态不能迁移成生产数据。

## 已知后端核对项
- 智能体控制器使用模板规则式查询，并非通用LLM。
- 模型explode接口目前不能据此认定具备真实拆解和自动BOM提取。
- 模型转换含Windows路径与本地Python依赖，部署可用性待确认。
- 项目级成员角色与细粒度授权需单独验证；前端权限草稿没有安全效力。
- 待办、会话、附件、模型数据流的跨账号访问范围需做正反向测试；若后端存在越权，记录为阻断，不通过前端掩盖。

## 原型迁移要求
V6.2采用多次函数包装与追加CSS，适合验证、不宜继续作为生产结构扩展。正式实现应拆成组件、消除重复覆盖、集中token与导航配置。保留现有构建和业务回归，不引入与任务无关的框架更换。
