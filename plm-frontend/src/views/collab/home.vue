<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>项目协同 · 状态看板</h3>
        <el-checkbox-group v-model="visibleCols">
          <el-checkbox v-for="c in COLUMNS" :key="c.key" :value="c.key" :label="c.key">{{ c.label }}</el-checkbox>
        </el-checkbox-group>
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="kanban">
        <div v-for="c in COLUMNS" :key="c.key" v-show="visibleCols.includes(c.key)" class="col" :class="c.key">
          <div class="col-head">
            <span>{{ c.label }}</span><b>{{ byStatus(c.key).length }}</b>
          </div>
          <div class="col-body">
            <div v-for="r in byStatus(c.key)" :key="r.id" class="pcard">
              <div class="pc-top" @click="open(r)">
                <span class="chev">{{ expanded.has(r.id) ? '▾' : '▸' }}</span>
                <span class="no">{{ r.projectNo }}</span>
                <span class="prog">{{ r.nodeDone }}/{{ r.nodeTotal }}</span>
              </div>
              <div class="pc-name" @click="open(r)">{{ r.projectName }}</div>
              <div class="pc-meta">{{ r.owner || '—' }} · {{ r.currentStage || '—' }}</div>
              <!-- 展开子项(节点) -->
              <div v-if="expanded.has(r.id)" class="subs">
                <span v-for="n in nodeItems(r)" :key="n.code" class="sub" :class="n.cls" :title="n.label">
                  {{ n.label }}
                </span>
              </div>            </div>
            <p v-if="!byStatus(c.key).length" class="muted">无</p>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { pageTracking, getTrackingNodeDefs } from '@/api/project'

const COLUMNS = [
  { key: 'ACTIVE', label: '进行中' },
  { key: 'ON_HOLD', label: '暂停' },
  { key: 'MP', label: '量产' },
  { key: 'CLOSED', label: '关闭' }
]

const rows = ref([])
const nodeDefs = ref([])
const loading = ref(false)
const visibleCols = ref(['ACTIVE', 'ON_HOLD', 'MP', 'CLOSED'])
const expanded = reactive(new Set())

const byStatus = (s) => rows.value.filter((r) => r.status === s)

function nodeItems(r) {
  const items = []
  for (const d of nodeDefs.value) {
    const v = r.cells && r.cells[d.code]
    if (!v) continue // 未设置节点不显示
    let cls = 'n'
    if (r.doneCells && r.doneCells[d.code]) cls = 'n done'
    else if (r.overdueCells && r.overdueCells[d.code]) cls = 'n od'
    else if (/^\d{4}-\d{2}-\d{2}/.test(v)) cls = 'n date'
    items.push({ code: d.code, label: d.label, cls })
  }
  return items
}
function toggle(id) {
  expanded.has(id) ? expanded.delete(id) : expanded.add(id)
  expanded = reactive(expanded)
}
function open(r) {
  // 项目卡片点击 → 连接到「我的待办」并按该项目过滤
  router.push({ path: '/project/tasks', query: { projectNo: r.projectNo } })
}

async function load() {
  loading.value = true
  try {
    const [t, d] = await Promise.all([
      pageTracking({ pageNum: 1, pageSize: 200 }),
      getTrackingNodeDefs()
    ])
    rows.value = t.data.records || []
    nodeDefs.value = d.data || []
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.kanban { display: flex; gap: 12px; align-items: flex-start; overflow-x: auto; }
.col { flex: 1; min-width: 220px; border: 1px solid #e4e7ed; border-radius: 10px; background: #f7f8fb; }
.col-head { padding: 8px 12px; font-size: 13px; font-weight: 600; display: flex; justify-content: space-between;
  border-bottom: 1px solid #e4e7ed; background: #fff; border-radius: 10px 10px 0 0; }
.col.ACTIVE .col-head { color: #409eff; } .col.ON_HOLD .col-head { color: #e6a23c; }
.col.MP .col-head { color: #67c23a; } .col.CLOSED .col-head { color: #909399; }
.col-body { padding: 8px; max-height: calc(100vh - 200px); overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.pcard { background: #fff; border: 1px solid #e4e7ed; border-radius: 8px; padding: 8px 10px; }
.pc-top { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.pc-top .chev { color: #c0c4cc; width: 12px; }
.pc-top .no { font-size: 12px; color: var(--plm-accent, #409eff); font-family: monospace; flex: 1; }
.pc-top .prog { font-size: 11px; color: #909399; font-family: monospace; }
.pc-name { font-size: 13px; font-weight: 600; margin: 2px 0 2px 18px; cursor: pointer; }
.pc-meta { font-size: 11px; color: #a8abb2; margin-left: 18px; }
.subs { display: flex; flex-wrap: wrap; gap: 4px; margin: 6px 0 0 18px; }
.sub { font-size: 11px; padding: 1px 6px; border-radius: 10px; background: #f0f2f5; color: #909399; }
.sub.done { background: #f0f9eb; color: #67c23a; }
.sub.od { background: #fef0f0; color: #f56c6c; }
.sub.date { background: #ecf5ff; color: #409eff; }
.muted { color: #c0c4cc; font-size: 12px; text-align: center; padding: 10px 0; }
</style>
