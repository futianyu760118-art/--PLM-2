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
.stat-card { display: flex; align-items: center; gap: 16px;
  .stat-info { .stat-num { font-size: 28px; font-weight: 700; color: #303133; } .stat-title { color: #909399; font-size: 13px; } .stat-sub { color:#c0c4cc; font-size:11px; margin-top:2px; } }
}
</style>
