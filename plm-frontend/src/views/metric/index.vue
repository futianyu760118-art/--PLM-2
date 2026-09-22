<template>
  <div class="page-container">
    <OperationGuide module-key="metric" />
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header><span>质量均分</span></template>
          <div ref="dqChartRef" style="height:280px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>ECN 闭环时长</span></template>
          <div ref="ecnChartRef" style="height:280px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>KPI 实时</span>
          <el-button-group>
            <el-button size="small" @click="refresh" :loading="loading">刷新</el-button>
            <el-button size="small" type="primary" @click="runNightScan">触发DQ夜检</el-button>
          </el-button-group>
        </div>
      </template>
      <el-table :data="kpiList" border size="small" v-loading="loading">
        <el-table-column prop="kpiCode" label="KPI" width="200" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="actualValue" label="实际值" width="120" align="right" />
        <el-table-column prop="periodKey" label="周期" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="kpiTag(row.status)" size="small">{{ row.status || '—' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { kpiValues } from '@/api/metric'
import { materialStatusDist, ecnTrend } from '@/api/dashboard'
import { runJob } from '@/api/job'

const dqChartRef = ref()
const ecnChartRef = ref()
let dqChart, ecnChart

const loading = ref(false)
const kpiList = ref([])

const kpiTag = (s) => ({ GREEN: 'success', YELLOW: 'warning', RED: 'danger', GRAY: 'info' }[s] || 'info')

async function loadKpis() {
  loading.value = true
  try {
    const r = await kpiValues()
    kpiList.value = r.data || []
  } finally { loading.value = false }
}

async function loadCharts() {
  const statusRes = await materialStatusDist()
  dqChart = echarts.init(dqChartRef.value)
  dqChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: (statusRes.data || []).map(r => ({ name: r.status, value: r.count }))
    }]
  })

  const trendRes = await ecnTrend()
  ecnChart = echarts.init(ecnChartRef.value)
  ecnChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '8%', containLabel: true },
    xAxis: { type: 'category', data: (trendRes.data || []).map(r => r.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', data: (trendRes.data || []).map(r => r.count), smooth: true, areaStyle: {} }]
  })
}

function refresh() { loadKpis() }

async function runNightScan() {
  try {
    const r = await runJob('dq/scan')
    ElMessage.success(`夜检完成: 扫描 ${r.data.scanned} 个料号`)
    refresh()
  } catch (e) {
    ElMessage.error('夜检失败')
  }
}

function onResize() {
  dqChart?.resize()
  ecnChart?.resize()
}

onMounted(async () => {
  await loadKpis()
  await loadCharts()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  dqChart?.dispose()
  ecnChart?.dispose()
})
</script>