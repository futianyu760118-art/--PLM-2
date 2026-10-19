<template>
  <div class="page-container">
    <el-card>
      <div class="g-head">
        <h3>项目甘特图</h3>
        <el-select v-model="selectedIds" multiple filterable collapse-tags collapse-tags-tooltip clearable
          placeholder="选择项目（可单选/多选，不选显示全部）" style="width:340px" @change="noop">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.project_no} ${p.project_name}`" :value="String(p.id)" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width:130px" @change="load">
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已量产" value="MP" />
          <el-option label="已关闭" value="CLOSED" />
          <el-option label="暂停" value="ON_HOLD" />
        </el-select>
        <el-button icon="Refresh" @click="load">刷新</el-button>
        <el-button :disabled="!rows.some(r => r.type === 'project')" @click="expandAll">全部展开</el-button>
        <el-button @click="collapseAll">全部收起</el-button>
        <div class="zoom">
          <span>缩放</span>
          <el-button size="small" :disabled="dayWidth <= 3" @click="setZoom(-3)">-</el-button>
          <el-button size="small" :disabled="dayWidth >= 24" @click="setZoom(3)">+</el-button>
        </div>
        <div class="legend">
          <span class="lg"><i class="dot st-ACTIVE"></i>进行中</span>
          <span class="lg"><i class="dot st-MP"></i>已量产</span>
          <span class="lg"><i class="dot st-CLOSED"></i>已关闭</span>
          <span class="lg"><i class="dot st-ON_HOLD"></i>暂停</span>
          <span class="lg"><i class="ms"></i>关键节点</span>
        </div>
      </div>

      <div v-loading="loading" class="gantt">
        <div class="g-left">
          <div class="gl-head">项目 / 节点</div>
          <div v-for="r in rows" :key="r.id" class="gl-row" :class="r.type === 'node' ? 'gl-node' : 'st-' + r.project.status">
            <template v-if="r.type === 'project'">
              <div class="gl-name" @click="toggle(r.project.id)">
                <el-icon class="gl-exp" :class="{ open: isExpanded(r.project.id) }"><ArrowRight /></el-icon>
                {{ r.project.project_name || r.project.project_no }}
              </div>
              <div class="gl-meta">{{ r.project.project_no }} · {{ r.project.owner }}</div>
            </template>
            <template v-else>
              <div class="gl-node-name" :title="r.node.node_name">{{ r.node.node_name }}</div>
            </template>
          </div>
          <el-empty v-if="!rows.length" description="暂无项目" :image-size="60" />
        </div>

        <div class="g-right">
          <div class="gr-scroll" :style="{ width: totalWidth + 'px' }">
            <div class="gr-axis">
              <div v-for="m in months" :key="m.key" class="gr-month" :style="{ left: m.left + 'px', width: m.width + 'px' }">{{ m.label }}</div>
            </div>
            <div class="gr-grid">
              <div v-for="t in weekTicks" :key="t.left" class="gr-tick" :style="{ left: t.left + 'px' }"></div>
            </div>
            <div v-if="todayLeft != null" class="gr-today" :style="{ left: todayLeft + 'px' }"></div>
            <div v-for="r in rows" :key="r.id" class="gr-row">
              <template v-if="r.type === 'project'">
                <div class="gr-bar" :class="'st-' + r.project.status" :style="barStyle(r.project)"
                  :title="`${r.project.project_no} ${r.project.project_name}\n${d2d(r.project.start_date)} ~ ${d2d(r.project.end_date)}`">
                  <span class="bar-label">{{ d2d(r.project.start_date) }} ~ {{ d2d(r.project.end_date) }}</span>
                </div>
                <div v-for="m in r.project.milestones" :key="m.node_code" class="gr-ms" :class="{ done: m.status === 'DONE' }"
                  :style="{ left: msLeft(m.date) + 'px' }" :title="`${m.node_name} · ${d2d(m.date)}${m.status === 'DONE' ? '（已完成）' : ''}`">◆</div>
              </template>
              <template v-else>
                <div v-if="nd(r.node)" class="gr-node" :class="nodeCls(r.node)" :style="nodeStyle(r.node)"
                  :title="`${r.node.node_name}${nd(r.node).start ? ' · ' + nd(r.node).start + (nd(r.node).end && nd(r.node).end !== nd(r.node).start ? ' ~ ' + nd(r.node).end : '') : ''}`"></div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getGantt } from '@/api/project'

const loading = ref(false)
const projects = ref([])
const statusFilter = ref('')
const selectedIds = ref([])
const expandedIds = ref([])
const dayWidth = ref(8)

const displayProjects = computed(() => {
  if (!selectedIds.value.length) return projects.value
  const set = new Set(selectedIds.value)
  return projects.value.filter(p => set.has(String(p.id)))
})

const rows = computed(() => {
  const out = []
  for (const p of displayProjects.value) {
    out.push({ type: 'project', project: p, id: 'p' + p.id })
    if (expandedIds.value.includes(String(p.id))) {
      for (const n of (p.nodes || [])) {
        out.push({ type: 'node', project: p, node: n, id: 'n' + p.id + n.node_code })
      }
    }
  }
  return out
})

function isExpanded(id) { return expandedIds.value.includes(String(id)) }
function toggle(id) {
  const k = String(id)
  const i = expandedIds.value.indexOf(k)
  if (i >= 0) expandedIds.value.splice(i, 1)
  else expandedIds.value.push(k)
}
function expandAll() { expandedIds.value = displayProjects.value.map(p => String(p.id)) }
function collapseAll() { expandedIds.value = [] }
function noop() {}

function d2d(v) { return v ? String(v).slice(0, 10) : '' }
function day(d) { const s = d2d(d); return s ? Math.floor(new Date(s + 'T00:00:00Z').getTime() / 86400000) : 0 }

const range = computed(() => {
  let min = Infinity, max = -Infinity
  for (const p of displayProjects.value) {
    const s = day(p.start_date), e = day(p.end_date)
    if (s && e) { if (s < min) min = s; if (e > max) max = e }
  }
  if (min === Infinity) { min = day(new Date().toISOString().slice(0, 10)) - 30; max = min + 90 }
  const today = day(new Date().toISOString().slice(0, 10))
  if (max < today + 14) max = today + 14
  return { start: min - 7, end: max + 7 }
})

const totalDays = computed(() => range.value.end - range.value.start)
const totalWidth = computed(() => totalDays.value * dayWidth.value)

function x(d) { return (day(d) - range.value.start) * dayWidth.value }
function barStyle(p) {
  const s = day(p.start_date), e = day(p.end_date)
  return { left: x(p.start_date) + 'px', width: Math.max((e - s + 1) * dayWidth.value, 4) + 'px' }
}
function msLeft(d) { return x(d) - 4 + 'px' }

function nd(n) {
  const p = d2d(n.plan_date), a = d2d(n.actual_date)
  if (!p && !a) return null
  const start = p || a
  const end = a || p
  return { start, end }
}
function nodeStyle(n) {
  const d = nd(n)
  const s = day(d.start), e = day(d.end)
  return { left: x(d.start) + 'px', width: Math.max((e - s + 1) * dayWidth.value, 4) + 'px' }
}
function nodeCls(n) {
  if (n.status === 'DONE') return 'done'
  const p = d2d(n.plan_date)
  const today = new Date().toISOString().slice(0, 10)
  if (p && p < today) return 'overdue'
  return 'planned'
}

const todayLeft = computed(() => {
  const t = day(new Date().toISOString().slice(0, 10))
  if (t < range.value.start || t > range.value.end) return null
  return x(new Date().toISOString().slice(0, 10)) + 'px'
})

const months = computed(() => {
  const arr = []
  const cur = new Date(range.value.start * 86400000)
  const end = new Date(range.value.end * 86400000)
  let m = new Date(Date.UTC(cur.getUTCFullYear(), cur.getUTCMonth(), 1))
  while (m <= end) {
    const next = new Date(Date.UTC(m.getUTCFullYear(), m.getUTCMonth() + 1, 1))
    const startDay = Math.floor(m.getTime() / 86400000)
    const endDay = Math.floor(next.getTime() / 86400000)
    const left = Math.max(0, (startDay - range.value.start) * dayWidth.value)
    const width = Math.max((Math.min(endDay, range.value.end) - Math.max(startDay, range.value.start)) * dayWidth.value, 1)
    arr.push({ key: m.toISOString().slice(0, 7), label: `${m.getUTCFullYear()}-${String(m.getUTCMonth() + 1).padStart(2, '0')}`, left, width })
    m = next
  }
  return arr
})

const weekTicks = computed(() => {
  const arr = []
  for (let d = range.value.start; d <= range.value.end; d++) {
    const dt = new Date(d * 86400000)
    if (dt.getUTCDay() === 1) arr.push({ left: (d - range.value.start) * dayWidth.value })
  }
  return arr
})

function setZoom(d) { dayWidth.value = Math.min(24, Math.max(3, dayWidth.value + d)) }

async function load() {
  loading.value = true
  try {
    const res = await getGantt(statusFilter.value || undefined)
    projects.value = res.data?.projects || []
  } finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped lang="scss">
.g-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px;
  h3 { margin: 0; margin-right: auto; font-size: 15px; }
  .zoom { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #909399; }
  .legend { display: flex; gap: 12px; font-size: 12px; color: #606266; flex-wrap: wrap;
    .lg { display: inline-flex; align-items: center; gap: 4px; }
    .dot { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
    .ms { width: 8px; height: 8px; transform: rotate(45deg); background: #f56c6c; display: inline-block; } }
}
.gantt { display: flex; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; }
.g-left { flex: 0 0 260px; border-right: 1px solid #e4e7ed; background: #fafbfc; }
.gl-head { padding: 10px 12px; font-weight: 600; font-size: 13px; border-bottom: 1px solid #e4e7ed; height: 34px; box-sizing: border-box; }
.gl-row { padding: 8px 12px; border-bottom: 1px solid #f0f2f5; height: 44px; box-sizing: border-box; overflow: hidden; }
.gl-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; display: flex; align-items: center; }
.gl-exp { font-size: 13px; color: #909399; transition: transform .15s; flex: 0 0 auto; }
.gl-exp.open { transform: rotate(90deg); }
.gl-meta { font-size: 11px; color: #909399; }
.gl-node { padding-left: 26px; height: 34px; }
.gl-node-name { font-size: 12px; color: #606266; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.g-right { flex: 1; overflow-x: auto; }
.gr-scroll { position: relative; }
.gr-axis { position: sticky; top: 0; z-index: 3; height: 34px; background: #fafbfc; border-bottom: 1px solid #e4e7ed; }
.gr-month { position: absolute; top: 0; height: 34px; line-height: 34px; font-size: 12px; color: #606266; border-left: 1px solid #e4e7ed; padding-left: 6px; white-space: nowrap; }
.gr-grid { position: absolute; top: 34px; bottom: 0; width: 100%; z-index: 0; }
.gr-tick { position: absolute; top: 0; bottom: 0; width: 1px; background: #f0f2f5; }
.gr-today { position: absolute; top: 34px; bottom: 0; width: 2px; background: #f56c6c; z-index: 2; }
.gr-row { position: relative; height: 44px; border-bottom: 1px solid #f0f2f5; }
.gr-bar { position: absolute; top: 12px; height: 20px; border-radius: 4px; display: flex; align-items: center; padding: 0 6px; overflow: hidden; cursor: pointer;
  &.st-ACTIVE { background: #409eff; color: #fff; }
  &.st-MP { background: #67c23a; color: #fff; }
  &.st-CLOSED { background: #c0c4cc; color: #fff; }
  &.st-ON_HOLD { background: #e6a23c; color: #fff; }
  .bar-label { font-size: 11px; white-space: nowrap; } }
.gr-ms { position: absolute; top: 17px; font-size: 12px; color: #909399; transform: rotate(45deg); cursor: pointer; z-index: 2;
  &.done { color: #67c23a; } }
.gr-node { position: absolute; top: 15px; height: 14px; border-radius: 3px; cursor: pointer;
  &.done { background: #67c23a; }
  &.overdue { background: #f56c6c; }
  &.planned { background: #409eff; } }
</style>
