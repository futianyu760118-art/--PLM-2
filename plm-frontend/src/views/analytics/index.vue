<template>
  <div class="page-container">
    <OperationGuide module-key="analytics" />
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.code" :span="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="chart-hdr">
              <b>{{ card.title }}</b>
              <el-tag size="small">{{ card.chartType }}</el-tag>
            </div>
          </template>
          <div class="chart-body">
            <template v-if="card.chartType==='TABLE'">
              <el-table :data="card.rows" border size="small" max-height="240">
                <el-table-column v-for="col in card.columns||[]" :key="col.label" :prop="col.field" :label="col.label" :width="col.width||120">
                  <template #default="{row}" v-if="col.tagMap">
                    <el-tag :type="col.tagMap[row[col.field]]||'info'" size="small">{{ row[col.field] }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <div v-else ref="el" :style="`height:240px`">
              <div v-if="!card.rows.length" class="empty">暂无数据</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { listCharts, chartData } from '@/api/analytics'

const charts = []
const cards = reactive([])
const chartInstances = []

function renderChart(el, config, rows) {
  if (!el) return
  const x = rows.map(r => r[config.xField])
  let series, yName
  if (config.chartType === 'PIE') {
    series = rows.map(r => ({ name: r[config.nameField], value: Number(r[config.valueField]) }))
    return echarts.init(el).setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['40%','70%'], data: series }]
    })
  }
  series = [{
    name: config.title,
    type: 'line',
    data: rows.map(r => Number(r[config.yField])),
    smooth: config.smooth !== false,
    areaStyle: config.color ? { color: hexAlpha(config.color, 0.3) } : undefined,
    itemStyle: { color: config.color || '#409eff' },
    lineStyle: { width: 3 }
  }]
  if (config.chartType === 'BAR') series[0].type = 'bar'
  return echarts.init(el).setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: x, axisLabel: { rotate: 25 } },
    yAxis: { type: 'value', max: config.max || null },
    series
  })
}

function hexAlpha(hex, a) {
  if (!hex || !hex.startsWith('#')) return hex
  const r = parseInt(hex.slice(1,3), 16), g = parseInt(hex.slice(3,5), 16), b = parseInt(hex.slice(5,7), 16)
  return { type: 'linear', x:0,y:0,x2:0,y2:1, colorStops:[{offset:0,color:`rgba(${r},${g},${b},${a})`},{offset:1,color:`rgba(${r},${g},${b},0.05)`}] }
}

async function loadAll() {
  const chartRes = await listCharts()
  const list = chartRes.data || []
  await nextTick()
  const refs = charts
  for (let i = 0; i < list.length; i++) {
    const ch = list[i]
    let encode = {}
    try { encode = JSON.parse(ch.encodeJson || '{}') } catch {}
    const dataRes = await chartData(ch.chartCode)
    const rows = dataRes.data?.rows || []
    cards.push({ code: ch.chartCode, title: ch.title, chartType: ch.chartType, rows, columns: encode.columns, encode })
  }
  await nextTick()
  cards.forEach((c, idx) => {
    if (['LINE','BAR','PIE'].includes(c.chartType) && refs[idx]) {
      const inst = renderChart(refs[idx], { ...c.encode, chartType: c.chartType, title: c.title }, c.rows)
      if (inst) chartInstances.push(inst)
    }
  })
}

function onResize() { chartInstances.forEach(c => c?.resize()) }
onMounted(loadAll)
onBeforeUnmount(() => { chartInstances.forEach(c => c?.dispose()); window.removeEventListener('resize', onResize) })
</script>

<style scoped lang="scss">
.chart-hdr { display: flex; justify-content: space-between; align-items: center }
.chart-body { min-height: 240px }
.chart-card { margin-bottom: 16px }
.empty { padding: 80px 0; text-align: center; color: #999 }
</style>
