<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card>
          <h3 class="title">使用说明</h3>
          <el-input v-model="guideKw" placeholder="搜索说明" clearable size="small" style="margin-bottom:10px" prefix-icon="Search" />
          <el-collapse>
            <el-collapse-item v-for="g in filteredGuides" :key="g.t" :title="g.t">
              <ul class="guide-list">
                <li v-for="item in g.items" :key="item">{{ item }}</li>
              </ul>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card>
          <h3 class="title">学习教材（点击课程展开，进度保存在本机）</h3>
          <el-collapse v-model="openCourses" accordion>
            <el-collapse-item v-for="c in COURSES" :key="c.id" :name="c.id">
              <template #title>
                <label class="course-head" @click.stop>
                  <input v-model="readMap[c.id]" type="checkbox" @change="persist" />
                  <span class="cname" :class="{ read: readMap[c.id] }">{{ c.name }}</span>
                </label>
              </template>
              <div class="lesson">
                <p v-for="(s, i) in c.steps" :key="i" class="step">
                  <b v-if="s.k">{{ s.k }}</b>{{ s.v }}
                </p>
              </div>
            </el-collapse-item>
          </el-collapse>
          <div class="progress-row">
            <el-progress :percentage="progress" />
            <el-button size="small" @click="resetRead">重置阅读进度</el-button>
          </div>
          <p class="note">阅读进度仅用于个人学习记录，不影响业务数据与外观设置。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const guideKw = ref('')
const GUIDES = [
  { t: '五种模式怎么用', items: [
    '顶部模式条可切换：01 原版业务 / 02 产品工作空间 / 03 专业视图 / 04 项目协同 / 05 Multica参考。',
    '原版业务保持原有分组与入口，随时可回退；其他模式只是重新分组导航，不改变权限与数据。',
    '点击模式名会直接进入该模式主页（如 04 项目协同→项目卡片看板）。' ] },
  { t: '项目明细表（核心操作页）', items: [
    '位置：研发自治中心 → 项目明细表。选项目后显示 22 个节点。',
    '行内直接编辑：状态下拉、计划/实际日期选择、责任人输入，改完即时保存；清空日期也生效（清实际日期自动降级状态）。',
    '填计划日期自动推断状态：未到→已计划；已到/已过→进行中；上传证据后自动已完成；「确认完成」一键置已完成+当天日期。',
    '点行首箭头展开：完整表单、证据文件上传、追加子项（如模具重开）、填工作表。',
    '「待办」状态必须填计划日期才能保存。',
    '排序：点 #/节点/状态/日期/责任人 表头；查找：项目选择旁的「节点查找」框。' ] },
  { t: '阶段门与节点完成标准', items: [
    '22 节点全部达成对应门的要求时，阶段门自动进阶（G0→G8）；G8 达成自动关闭项目。',
    '完成硬标准：状态=已完成 且 填实际日期 且 证据≥1。',
    '项目暂停（状态改 ON_HOLD）时，未完成节点自动置为暂停，之后仍可手动改。' ] },
  { t: '我的待办 / 部门待办', items: [
    '我的待办：我负责项目的节点 + 直接指定给我的节点，分 逾期/待办/已完成 三段。',
    '部门待办（经理角色可见）：本部门成员待办聚合，姓名标注；个人待办自动汇总成部门待办。',
    '支持排序（点表头）、关键字查找（顶部搜索框）、行内改日期（即时保存）。',
    '点「处理」直接定位到该项目该节点的明细表位置。' ] },
  { t: '研发项目跟踪总表', items: [
    '一行一项目 × 22 节点；点单元格即可编辑（V/X/进行中/待定/日期）。',
    '项目状态列（负责人右侧）可下拉直接调整项目状态（进行中/已量产/已关闭/暂停/取消）。',
    '颜色：绿=已完成（显示实际日期）、蓝=计划日期、红=逾期、黄=进行中。「无」=该节点不适用（不计入完成统计）。' ] },
  { t: '项目甘特图', items: [
    '04 项目协同 → 项目甘特图：真实数据；甘特图Demo套装：7 种图型演示。',
    '支持选项目（单选/多选）、日/周/月粒度切换、+/- 缩放、点项目名展开 22 节点明细。',
    '已完成节点显示绿色。' ] },
  { t: '工作表与智能导入', items: [
    '9 类工作表：计划/基础BOM/规格/配置/样品/评审/送检测试/试产/出货。',
    '从明细表点「填工作表」自动带出项目信息（编号/名称/客户/负责人/型号）。',
    '「智能导入」：一个 Excel 多个 Sheet 自动识别目标工作表（按Sheet名+表头关键词），中文列名自动映射字段，当前有项目筛选时自动补项目编号。' ] },
  { t: '问题反馈', items: [
    '右下角悬浮球（可拖拽）→ 非模态抽屉；「收起」变胶囊，点胶囊再展开。',
    '两个页签：提交反馈 / 反馈记录（进度、AI处理状态、回复、状态下拉调整）。',
    '提交后 AI 自动接手生成初步处理方案；真正落地由管理员跟进。',
    '反馈状态流：待处理→已初步答复→批准实现→实现中→已落地→已闭环/关闭。' ] },
  { t: '智能体', items: [
    '右上角「PLM智能体」：通俗对话查项目/待办/逾期、打开页面。',
    '支持多轮上下文（追问「那它的节点进度呢」能理解），并从历史对话中学习，越用越准。',
    '结论仅供参考，关键决策需人工确认。' ] },
  { t: '知识库 / 统一录入 / 其他', items: [
    '知识库（工作台）：录入 ima 导出的文本，支持大模型问答检索。',
    '统一录入（工作台）：按分类进入各录入入口（立项/跟踪/明细/工作表/物料/证据/变更/BOM/3D/档案/外协）。',
    '物料建档向导：含图纸编号/版本 + 附图上传（CAD2007版本确认）。',
    '数据可见性：右上角可切「看全部/只看别人/只看自己」（管理员不显示）。' ] }
]

const filteredGuides = computed(() => {
  const kw = guideKw.value.trim().toLowerCase()
  if (!kw) return GUIDES
  return GUIDES.map(g => ({ ...g, items: g.items.filter(i => i.toLowerCase().includes(kw) || g.t.toLowerCase().includes(kw)) })).filter(g => g.items.length)
})

const COURSES = [
  { id: 'c1', name: '第1课 系统总览与登录', steps: [
    { k: '登录：', v: '浏览器打开 http://192.168.0.71，输入账号密码（初始 admin/admin@123，首次登录后请改密）。' },
    { k: '首页：', v: '工作台看板展示我的项目/待办/证据等即时数据。' },
    { k: '模式：', v: '顶部五种界面模式只是导航重组，数据权限不变；建议日常用「01 原版业务」。' },
    { k: '帮助：', v: '任何时候顶部「帮助与学习」可回到本页。' } ] },
  { id: 'c2', name: '第2课 研发项目与立项', steps: [
    { k: '立项：', v: '研发自治中心 → 立项申请 → 新建立项（客户/产品/负责人/预期周期），提交后走审批。' },
    { k: '转项目：', v: '立项批准后自动生成项目编号并转入研发项目列表。' },
    { k: '明细表：', v: '项目明细表选项目，系统自动初始化 22 个节点。' } ] },
  { id: 'c3', name: '第3课 跟踪表与节点完成', steps: [
    { k: '填节点：', v: '明细表行内直接选状态/改日期/改责任人，即时保存；或跟踪总表点单元格编辑。' },
    { k: '传证据：', v: '明细表展开节点 → 上传证据文件 → 节点自动置已完成并填当天日期。' },
    { k: '标准：', v: '完成 = 已完成状态 + 实际日期 + 证据≥1；填计划日期自动推断已计划/进行中。' },
    { k: '子项：', v: '展开节点 → 追加子项（如模具重开记录），仅项目负责人/管理员可加。' },
    { k: '暂停：', v: '项目状态改为暂停后，未完成节点自动置暂停，可再手动改回。' } ] },
  { id: 'c4', name: '第4课 物料/BOM/变更', steps: [
    { k: '建档：', v: '物料主数据 → 建档向导：选品类→基础信息（含图纸编号/版本/附图上传）→参数→自检提交。' },
    { k: 'BOM：', v: 'BOM产品结构维护多级物料清单。' },
    { k: '变更：', v: 'ECN工程变更管理版本切换。' } ] },
  { id: 'c5', name: '第5课 工作表与档案', steps: [
    { k: '填表：', v: '明细表节点旁「填工作表」自动带项目信息；或工作表页直接新增。' },
    { k: '导入：', v: '工作表页「智能导入」支持多Sheet Excel 自动识别（规格书/配置表/计划表等），中文表头自动对齐，自动补项目编号。' },
    { k: '回填：', v: '工作表导入后自动触发完成判定，回填明细表节点状态。' } ] },
  { id: 'c6', name: '第6课 智能体与协作', steps: [
    { k: '对话：', v: '右上角 PLM智能体，输入如「HJ.26042 进度怎样」「我的待办」「打开跟踪」；支持追问（会记住上文）。' },
    { k: '待办：', v: '协同任务页看我的/部门待办，可排序、查找、行内改日期；点处理直达节点。' },
    { k: '反馈：', v: '右下角悬浮球提交问题/建议，反馈记录页签看进度与AI方案。' } ] }
]

const openCourses = ref('c1')
const LS_KEY = 'plm_reading_v1'
const readMap = reactive({})
try {
  const saved = JSON.parse(localStorage.getItem(LS_KEY) || '{}')
  COURSES.forEach((c) => { readMap[c.id] = !!saved[c.id] })
} catch { /* ignore */ }

const progress = computed(() => Math.round((COURSES.filter((c) => readMap[c.id]).length / COURSES.length) * 100))

function persist() {
  localStorage.setItem(LS_KEY, JSON.stringify({ ...readMap }))
}
function resetRead() {
  COURSES.forEach((c) => { readMap[c.id] = false })
  persist()
}
</script>

<style scoped lang="scss">
.title { margin: 0 0 10px; font-size: 15px; }
.guide-list { margin: 0; padding-left: 18px; font-size: 13px; color: #606266; line-height: 1.9; }
.course-head { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; width: 100%;
  .cname { font-weight: 500; }
  .cname.read { color: #67c23a; } }
.lesson { padding: 4px 8px; }
.step { margin: 0 0 8px; font-size: 13px; color: #606266; line-height: 1.8;
  b { color: #303133; margin-right: 4px; } }
.progress-row { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.note { margin: 10px 0 0; font-size: 12px; color: #909399; }
</style>
