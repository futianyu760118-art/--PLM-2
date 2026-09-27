<template>
  <div class="page-container">
    <el-row :gutter="12" class="stat-row">
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num">{{ summary.total || 0 }}</div><div class="lbl">项目总数</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num c-green">{{ summary.active || 0 }}</div><div class="lbl">进行中</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num c-blue">{{ summary.mp || 0 }}</div><div class="lbl">量产</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num c-gray">{{ summary.closed || 0 }}</div><div class="lbl">已关闭</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num c-orange">{{ summary.hold || 0 }}</div><div class="lbl">暂停</div></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="hover"><div class="stat"><div class="num c-red">{{ delayList.length }}</div><div class="lbl">逾期项目</div></div></el-card></el-col>
    </el-row>

    <el-card class="mt12">
      <template #header><b>月度立项统计</b></template>
      <el-table :data="monthly" border stripe size="small" empty-text="暂无数据">
        <el-table-column prop="ym" label="月份" width="140" />
        <el-table-column prop="cnt" label="立项数" />
      </el-table>
    </el-card>

    <el-card class="mt12">
      <template #header><b>按类型 / 阶段统计</b></template>
      <el-table :data="byCategory" border stripe size="small" empty-text="暂无数据">
        <el-table-column prop="project_type" label="项目类型" width="160" />
        <el-table-column prop="gate" label="当前阶段(G门)" width="160" />
        <el-table-column prop="cnt" label="数量" />
      </el-table>
    </el-card>

    <el-card class="mt12">
      <template #header><b>逾期项目明细</b></template>
      <el-table :data="delayList" border stripe size="small" empty-text="暂无逾期项目">
        <el-table-column prop="project_no" label="项目编号" width="160" />
        <el-table-column prop="project_name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="target_date" label="目标日期" width="130" />
        <el-table-column prop="current_gate" label="阶段" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { analysisSummary, analysisMonthly, analysisByCategory, analysisDelay } from '@/api/project'

const summary = ref({})
const monthly = ref([])
const byCategory = ref([])
const delayList = ref([])

async function load() {
  const [s, m, c, d] = await Promise.all([
    analysisSummary(), analysisMonthly(), analysisByCategory(), analysisDelay()
  ])
  summary.value = s.data || {}
  monthly.value = m.data || []
  byCategory.value = c.data || []
  delayList.value = d.data || []
}
onMounted(load)
</script>

<style scoped>
.stat-row { margin-bottom: 0; }
.stat { text-align: center; padding: 6px 0; }
.stat .num { font-size: 26px; font-weight: 600; }
.stat .lbl { font-size: 12px; color: #888; margin-top: 4px; }
.c-green { color: #67c23a; } .c-blue { color: #409eff; } .c-gray { color: #909399; }
.c-orange { color: #e6a23c; } .c-red { color: #f56c6c; }
.mt12 { margin-top: 12px; }
</style>
