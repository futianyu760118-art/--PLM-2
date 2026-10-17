<template>
  <div class="page-container">
    <OperationGuide module-key="dashboard" />
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="stat-card">
            <el-icon :size="40" :color="card.color"><component :is="card.icon" /></el-icon>
            <div class="stat-info">
              <div class="stat-num">{{ card.num }}</div>
              <div class="stat-title">{{ card.title }}</div>
              <div class="stat-sub" v-if="card.sub">{{ card.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card>
          <template #header><span>物料类型分布</span></template>
          <div ref="typeChartRef" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>物料状态分布</span></template>
          <div ref="statusChartRef" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header><span>ECN 变更趋势 (近7天)</span></template>
      <div ref="trendChartRef" style="height:280px"></div>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><span>KPI 概览</span></template>
      <el-table :data="kpiList" border size="small" v-loading="kpiLoading">
        <el-table-column prop="kpiCode" label="KPI" width="200" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="actualValue" label="实际值" width="100" align="right" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="kpiTag(row.status)" size="small">{{ row.status || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="periodKey" label="周期" width="100" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px" v-loading="meLoading">
      <template #header>
        <span>我的即时数据（{{ me?.realName || userStore.realName || '' }}，跨模块按当前用户统计）</span>
      </template>
      <div v-if="me" class="me-grid">
        <router-link class="me-cell" :to="{ path: '/project/list', query: { keyword: me.realName } }">
          <span class="n">{{ me.projects.total }}</span>
          <span class="t">我的项目</span>
          <span class="s">进行中 {{ me.projects.active }} · 已关闭 {{ me.projects.closed }}</span>
        </router-link>
        <router-link class="me-cell" :to="{ path: '/project/tasks', query: {} }">
          <span class="n">{{ me.todos.open + me.todos.escalated }}</span>
          <span class="t">我的待办</span>
          <span class="s">已升级 {{ me.todos.escalated }} · 已完成 {{ me.todos.done }}</span>
        </router-link>
        <router-link class="me-cell warn" :to="{ path: '/project/tracking', query: {} }">
          <span class="n">{{ me.nodes.overdue }}</span>
          <span class="t">我的逾期节点</span>
          <span class="s">负责节点 {{ me.nodes.owned }} · 已完成 {{ me.nodes.done }}</span>
        </router-link>
        <router-link class="me-cell" :to="{ path: '/project/evidence', query: { keyword: me.realName } }">
          <span class="n">{{ me.evidence }}</span>
          <span class="t">我上传的证据</span>
          <span class="s">计划表(我负责) {{ me.planSheets }}</span>
        </router-link>
        <router-link class="me-cell" :to="{ path: '/admin/feedback', query: {} }">
          <span class="n">{{ me.feedback }}</span>
          <span class="t">我的反馈</span>
          <span class="s">今日操作留痕 {{ me.todayChanges }}</span>
        </router-link>
      </div>
      <div v-if="me && me.nodes.overdueList && me.nodes.overdueList.length" class="me-overdue">
        <b>逾期提醒：</b>
        <span v-for="o in me.nodes.overdueList" :key="o.project_no + o.node_code" class="ov-item">
          {{ o.project_no }} · {{ o.node_name }}（计划 {{ (o.plan_date || '').substring(0, 10) }}）
        </span>
      </div>
      <div v-if="me && me.projects.recent && me.projects.recent.length" class="me-recent">
        <b>我的最近项目：</b>
        <router-link v-for="p in me.projects.recent" :key="p.id" class="rc-item"
          :to="{ path: '/project/progress', query: { project: p.id } }">
          {{ p.project_no }} {{ p.project_name }}（{{ p.status }}/{{ p.current_gate }}）
        </router-link>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card>
          <template #header><span>欢迎使用恒剑光电 PLM 中台 V4.0</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="当前用户">{{ userStore.realName }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleLabel }}</el-descriptions-item>
            <el-descriptions-item label="系统版本">V4.0 Final</el-descriptions-item>
            <el-descriptions-item label="文档编号">HJ-PLM-V4.0-20260704</el-descriptions-item>
            <el-descriptions-item label="全局主键" :span="2">物料料号 PartNo (全库唯一关联)</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header><span>我的待办(待审批/待整改)</span></template>
          <div v-for="wi in workItems" :key="wi.id" style="padding:6px 0;border-bottom:1px solid #f0f0f0;display:flex;justify-content:space-between;align-items:center;gap:6px">
            <span style="font-size:13px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="wi.title">{{ wi.title }}</span>
            <el-tag :type="workItemTag(wi.status)" size="small">{{ wi.status }}</el-tag>
            <el-button link type="primary" size="small" @click="openWorkItemLedger(wi)">证据</el-button>
            <el-button v-if="wi.status !== 'DONE'" link type="success" size="small" @click="handleCompleteWork(wi)">完成</el-button>
          </div>
          <el-empty v-if="!workItems.length" description="暂无待办" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="24">
        <el-card>
          <template #header><span>近期洞察</span></template>
          <div v-for="ins in insights" :key="ins.id" style="padding:6px 0;border-bottom:1px solid #f0f0f0">
            <el-tag :type="ins.severity==='HIGH'?'danger':ins.severity==='MEDIUM'?'warning':'info'" size="small">{{ ins.severity }}</el-tag>
            <span style="margin-left:8px;font-size:13px">{{ ins.title }}</span>
          </div>
          <el-empty v-if="!insights.length" description="暂无洞察" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 待办(工作项)侧的引用证据区: 与证据台账同源(AC-A3-1/A3-5) -->
    <el-drawer v-model="ledgerDrawer.visible" :title="'关联证据 - ' + (ledgerDrawer.title || '')" size="820px">
      <LedgerEvidencePanel object-type="TASK" :object-id="ledgerDrawer.id" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'
import { getStats, materialTypeDist, materialStatusDist, ecnTrend } from '@/api/dashboard'
import { kpiValues } from '@/api/metric'
import { listInsights } from '@/api/issue'
import { myWorkItems, completeWorkItem } from '@/api/workitem'
import { meOverview } from '@/api/me'
import LedgerEvidencePanel from '@/components/ledger/LedgerEvidencePanel.vue'

const userStore = useUserStore()
const typeChartRef = ref(null)
const statusChartRef = ref(null)
const trendChartRef = ref(null)
let typeChart, statusChart, trendChart

const modules = ['物料主数据', 'ECN变更', 'BOM爆炸', '品质检验', '外协发图', 'RBAC权限', '日志溯源', '档案生命周期']

const kpiList = ref([])
const kpiLoading = ref(false)
const insights = ref([])
const workItems = ref([])
const me = ref(null)
const meLoading = ref(false)

async function loadMe() {
  meLoading.value = true
  try {
    const res = await meOverview()
    me.value = res.data || null
  } catch { me.value = null }
  finally { meLoading.value = false }
}
const ledgerDrawer = reactive({ visible: false, id: '', title: '' })

function openWorkItemLedger(wi) {
  ledgerDrawer.id = String(wi.id)
  ledgerDrawer.title = wi.title || wi.itemNo || ''
  ledgerDrawer.visible = true
}
const kpiTag = (s) => ({ GREEN: 'success', YELLOW: 'warning', RED: 'danger', GRAY: 'info' }[s] || 'info')
const workItemTag = (s) => ({ OPEN: 'warning', ESCALATED: 'danger', DONE: 'success' }[s] || 'info')

const statCards = ref([
  { title: '物料料号', num: 0, sub: '', icon: 'Box', color: '#409eff' },
  { title: 'ECN变更单', num: 0, sub: '', icon: 'Switch', color: '#e6a23c' },
  { title: 'BOM清单', num: 0, sub: '', icon: 'Connection', color: '#67c23a' },
  { title: '模具资产', num: 0, sub: '', icon: 'Grid', color: '#f56c6c' }
])

const typeMap = { FINISHED:'成品', SEMI:'半成品', PLASTIC:'塑胶件', HARDWARE:'五金件', STANDARD:'标准件' }
const statusMap = { DRAFT:'草稿', REVIEWING:'评审中', RELEASED:'正式发布', IN_PRODUCTION:'量产在用', CHANGING:'变更中', OBSOLETE:'作废', SEALED:'停产封存' }

const roleLabel = (() => {
  const map = { ADMIN:'系统管理员', ENGINEER:'研发工程师', QUALITY:'品质/生产', SALES:'销售', SUPPLIER:'外协供应商', CUSTOMER:'外部客户' }
  return map[userStore.roles[0]] || ''
})()

async function loadStats() {
  const res = await getStats()
  const d = res.data
  statCards.value[0].num = d.materialCount
  statCards.value[0].sub = `已发布 ${d.releasedMaterial}`
  statCards.value[1].num = d.ecnCount
  statCards.value[1].sub = `待审批 ${d.pendingEcn} · 已生效 ${d.effectiveEcn}`
  statCards.value[2].num = d.bomCount
  statCards.value[3].num = d.moldCount
  statCards.value[3].sub = `量产中 ${d.productionMold}`
}

async function loadCharts() {
  const [typeRes, statusRes, trendRes] = await Promise.all([materialTypeDist(), materialStatusDist(), ecnTrend()])
  await nextTick()
  typeChart = echarts.init(typeChartRef.value)
  typeChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: typeRes.data.map(r => ({ name: typeMap[r.type] || r.type, value: r.count })),
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 }
    }]
  })

  statusChart = echarts.init(statusChartRef.value)
  statusChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: statusRes.data.map(r => statusMap[r.status] || r.status), axisLabel: { rotate: 20 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: statusRes.data.map(r => r.count), itemStyle: { color: '#409eff', borderRadius: [4,4,0,0] }, barWidth: '50%' }]
  })

  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: trendRes.data.map(r => r.date), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line', data: trendRes.data.map(r => r.count), smooth: true,
      areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(64,158,255,0.5)'},{offset:1,color:'rgba(64,158,255,0.05)'}]) },
      itemStyle: { color: '#409eff' }, lineStyle: { width: 3 }
    }]
  })
}

function onResize() {
  typeChart?.resize(); statusChart?.resize(); trendChart?.resize()
}

onMounted(async () => {
  await loadStats()
  await loadCharts()
  loadKpis()
  loadInsightsFeed()
  loadWorkItems()
  loadMe()
  window.addEventListener('resize', onResize)
})

async function loadKpis() {
  kpiLoading.value = true
  try {
    const res = await kpiValues()
    kpiList.value = res.data || []
  } catch { kpiList.value = [] }
  finally { kpiLoading.value = false }
}

async function loadInsightsFeed() {
  try {
    const res = await listInsights('NEW')
    insights.value = (res.data || []).slice(0, 5)
  } catch { insights.value = [] }
}

async function loadWorkItems() {
  try {
    const res = await myWorkItems()
    workItems.value = (res.data || []).slice(0, 8)
  } catch { workItems.value = [] }
}

async function handleCompleteWork(wi) {
  await completeWorkItem(wi.id)
  ElMessage.success('待办已完成')
  loadWorkItems()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  typeChart?.dispose(); statusChart?.dispose(); trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.me-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.me-cell { border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px; text-decoration: none;
  display: flex; flex-direction: column; gap: 2px; background: #fff;
  transition: transform .12s cubic-bezier(.34,1.56,.64,1), box-shadow .15s ease;
  &:hover { box-shadow: 0 3px 10px rgba(64,158,255,.16); }
  &:active { transform: scale(.96); }
  .n { font-size: 26px; font-weight: 700; color: var(--plm-accent, #409eff); line-height: 1.15; }
  .t { font-size: 13px; color: #303133; font-weight: 600; }
  .s { font-size: 11px; color: #a8abb2; }
  &.warn .n { color: #f56c6c; } }
.me-overdue { margin-top: 12px; font-size: 12px; color: #f56c6c; display: flex; gap: 10px; flex-wrap: wrap;
  b { color: #303133; }
  .ov-item { background: #fef0f0; border-radius: 4px; padding: 2px 8px; } }
.me-recent { margin-top: 10px; font-size: 12px; color: #909399; display: flex; gap: 10px; flex-wrap: wrap; align-items: center;
  b { color: #303133; }
  .rc-item { color: var(--plm-accent, #409eff); text-decoration: none; background: #f5f7fa; border-radius: 4px; padding: 2px 8px; } }
.stat-card { display: flex; align-items: center; gap: 16px;
  .stat-info { .stat-num { font-size: 28px; font-weight: 700; color: #303133; } .stat-title { color: #909399; font-size: 13px; } .stat-sub { color:#c0c4cc; font-size:11px; margin-top:2px; } }
}
</style>
