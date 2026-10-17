<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading">
          <template #header><span>岗位行动</span></template>
          <div class="kpis">
            <div class="kpi warn" @click="$router.push('/project/tasks')">
              <b>{{ me?.todos?.open + me?.todos?.escalated || 0 }}</b><span>我的待办</span>
            </div>
            <div class="kpi danger" @click="$router.push('/project/tracking')">
              <b>{{ me?.nodes?.overdue || 0 }}</b><span>逾期节点</span>
            </div>
            <div class="kpi ok" @click="$router.push('/project/evidence')">
              <b>{{ me?.evidence || 0 }}</b><span>我的证据</span>
            </div>
          </div>
          <el-divider>逾期提醒</el-divider>
          <div v-for="o in (me?.nodes?.overdueList || [])" :key="o.project_no" class="line danger">
            {{ o.project_no }} · {{ o.node_name }}（计划 {{ (o.plan_date || '').slice(0, 10) }}）
          </div>
          <p v-if="!(me?.nodes?.overdueList || []).length" class="muted">无逾期，继续保持</p>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading">
          <template #header><span>工程评审</span></template>
          <div v-for="r in reviews" :key="r.review_no" class="line">
            <el-tag size="small" :type="r.conclusion === '通过' ? 'success' : 'warning'">{{ r.conclusion }}</el-tag>
            {{ r.project_no }} · {{ r.review_type }}（{{ (r.review_date || '').slice(0, 10) }}）
          </div>
          <p v-if="!reviews.length" class="muted">暂无评审记录</p>
          <el-divider>最近变更</el-divider>
          <div v-for="c in changes" :key="c.id" class="line">
            {{ c.project_no }} · {{ c.object_type }} {{ c.action }}（{{ (c.created_at || '').slice(5, 16) }}）
          </div>
          <p v-if="!changes.length" class="muted">暂无变更</p>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card>
          <template #header><span>工具画廊</span></template>
          <div class="tools">
            <div v-for="t in TOOLS" :key="t.to" class="tool" @click="$router.push(t.to)">
              <el-icon :size="22"><component :is="t.icon" /></el-icon>
              <span>{{ t.label }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { meOverview } from '@/api/me'
import request from '@/utils/request'

const me = ref(null)
const reviews = ref([])
const changes = ref([])
const loading = ref(false)

const TOOLS = [
  { label: '智能体中心', to: '/agent/list', icon: 'MagicStick' },
  { label: '数据自检', to: '/dq/debts', icon: 'DataLine' },
  { label: '度量KPI', to: '/metric/list', icon: 'TrendCharts' },
  { label: '分析驾驶舱', to: '/analytics/list', icon: 'DataAnalysis' },
  { label: '证据台账', to: '/project/evidence', icon: 'Collection' },
  { label: '帮助与学习', to: '/help', icon: 'QuestionFilled' }
]

onMounted(async () => {
  loading.value = true
  try {
    meOverview().then(r => (me.value = r.data)).catch(() => {})
    request({ url: '/rd/sheets/review', params: { pageNum: 1, pageSize: 5 } }).then(r => (reviews.value = r.data.records || [])).catch(() => {})
    request({ url: '/project/changes', params: { pageNum: 1, pageSize: 5 } }).then(r => (changes.value = (r.data.records || []))).catch(() => {})
  } finally { loading.value = false }
})
</script>

<style scoped>
.kpis { display: flex; gap: 10px; }
.kpi { flex: 1; text-align: center; border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px; cursor: pointer; transition: transform .12s; }
.kpi:active { transform: scale(.95); }
.kpi b { display: block; font-size: 22px; }
.kpi span { font-size: 12px; color: #909399; }
.kpi.warn b { color: #e6a23c; } .kpi.danger b { color: #f56c6c; } .kpi.ok b { color: #67c23a; }
.line { padding: 6px 0; border-bottom: 1px dashed #f0f0f0; font-size: 13px; }
.line.danger { color: #f56c6c; }
.muted { color: #c0c4cc; font-size: 13px; }
.tools { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.tool { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 16px 8px;
  border: 1px solid #e4e7ed; border-radius: 8px; cursor: pointer; font-size: 12px; color: #606266;
  transition: transform .12s cubic-bezier(.34,1.56,.64,1), box-shadow .15s; }
.tool:hover { box-shadow: 0 2px 8px rgba(64,158,255,.15); color: var(--plm-accent, #409eff); }
.tool:active { transform: scale(.94); }
</style>
