<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>我的待办</h3>
        <el-checkbox v-model="onlyActive" @change="noop">仅进行中项目</el-checkbox>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
          <el-radio-button value="group">按项目分组</el-radio-button>
        </el-radio-group>
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-alert v-if="projectNo" type="info" :closable="false" class="proj-scope">
        仅显示项目 <b>{{ projectNo }}</b>
        <el-button link type="primary" size="small" style="margin-left:10px" @click="clearProject">返回全部待办</el-button>
      </el-alert>

      <!-- 看板：三列横排 -->
      <div v-if="viewMode === 'board'" class="board">
        <div class="bcol od"><div class="bcol-head">逾期 <b>{{ d.overdue.length }}</b></div>
          <div v-for="t in d.overdue" :key="key(t)" class="item" @click="open(t)">
            <span class="nm">{{ t.project_no }}<b>·</b>{{ t.node_name }}</span>
            <span class="dt od">{{ fmt(t.plan_date) }}</span>
          </div>
          <p v-if="!d.overdue.length" class="muted">无</p>
        </div>
        <div class="bcol"><div class="bcol-head">待办 <b>{{ d.todo.length }}</b></div>
          <div v-for="t in d.todo" :key="key(t)" class="item" @click="open(t)">
            <span class="nm">{{ t.project_no }}<b>·</b>{{ t.node_name }}</span>
            <span class="dt">{{ t.plan_date ? fmt(t.plan_date) : '无到期' }}</span>
          </div>
          <p v-if="!d.todo.length" class="muted">无</p>
        </div>
        <div class="bcol"><div class="bcol-head">已完成 <b>{{ d.done.length }}</b></div>
          <div v-for="t in d.done" :key="key(t)" class="item" @click="open(t)">
            <span class="nm">{{ t.project_no }}<b>·</b>{{ t.node_name }}</span>
            <span class="dt">{{ t.actual_date ? fmt(t.actual_date) : '—' }}</span>
          </div>
          <p v-if="!d.done.length" class="muted">无</p>
        </div>
      </div>

      <!-- 按项目分组 -->
      <div v-else-if="viewMode === 'group'">
        <div v-for="g in groups" :key="g.project_no" class="pgroup">
          <div class="pg-head">
            <b>{{ g.project_no }}</b> {{ g.project_name }}
            <el-tag size="small" :type="tag(g.project_status)">{{ statusLabel(g.project_status) }}</el-tag>
            <span class="cnt">待办 {{ g.todo }} · 逾期 {{ g.overdue }} · 完成 {{ g.done }}</span>
          </div>
          <div class="pg-items">
            <span v-for="n in g.nodes" :key="key(n)" class="chip" :class="chipCls(n)" @click="open(n)">{{ n.node_name }}</span>
          </div>
        </div>
        <el-empty v-if="!groups.length" description="无进行中/匹配项目" :image-size="60" />
      </div>

      <!-- 列表：三块纵向同页 -->
      <template v-else>
        <section class="sec">
          <div class="sec-head od">逾期（{{ d.overdue.length }}）</div>
          <el-table v-if="d.overdue.length" :data="d.overdue" size="small" border stripe>
            <el-table-column prop="project_no" label="项目" width="160" />
            <el-table-column prop="node_name" label="节点" width="140" />
            <el-table-column label="计划日期" width="110"><template #default="{ row }">{{ fmt(row.plan_date) }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="open(row)">处理</el-button></template></el-table-column>
          </el-table>
          <p v-else class="muted">无逾期</p>
        </section>
        <section class="sec">
          <div class="sec-head">待办（{{ d.todo.length }}）</div>
          <el-table v-if="d.todo.length" :data="d.todo" size="small" border stripe>
            <el-table-column prop="project_no" label="项目" width="160" />
            <el-table-column prop="node_name" label="节点" width="140" />
            <el-table-column label="计划日期" width="110"><template #default="{ row }">{{ row.plan_date ? fmt(row.plan_date) : '无到期' }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="open(row)">处理</el-button></template></el-table-column>
          </el-table>
          <p v-else class="muted">无待办</p>
        </section>
        <section class="sec">
          <div class="sec-head">已完成（{{ d.done.length }}）</div>
          <el-table v-if="d.done.length" :data="d.done" size="small" border stripe>
            <el-table-column prop="project_no" label="项目" width="160" />
            <el-table-column prop="node_name" label="节点" width="140" />
            <el-table-column label="完成日期" width="110"><template #default="{ row }">{{ row.actual_date ? fmt(row.actual_date) : '—' }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="open(row)">查看</el-button></template></el-table-column>
          </el-table>
          <p v-else class="muted">无已完成</p>
        </section>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { myTodos } from '@/api/me'

const router = useRouter()
const route = useRoute()
const raw = ref({ overdue: [], todo: [], done: [] })
const viewMode = ref('list')
const onlyActive = ref(true)
const projectNo = ref(route.query.projectNo ? String(route.query.projectNo) : '')

const statusLabel = (s) => ({ ACTIVE: '进行中', MP: '已量产', CLOSED: '已关闭', ON_HOLD: '暂停' }[s] || s)
const tag = (s) => ({ ACTIVE: 'primary', MP: 'success', CLOSED: 'info', ON_HOLD: 'warning' }[s] || 'info')

const filterList = (list) => (projectNo.value ? list.filter((t) => t.project_no === projectNo.value) : list)

// 默认仅进行中项目；关闭开关则全部；再按项目过滤
const d = computed(() => {
  const base = onlyActive.value
    ? { overdue: raw.value.overdue.filter((t) => t.project_status === 'ACTIVE'), todo: raw.value.todo.filter((t) => t.project_status === 'ACTIVE'), done: raw.value.done.filter((t) => t.project_status === 'ACTIVE') }
    : raw.value
  return { overdue: filterList(base.overdue), todo: filterList(base.todo), done: filterList(base.done) }
})

function clearProject() {
  projectNo.value = ''
  router.replace({ path: '/project/tasks' })
}
watch(() => route.query.projectNo, (v) => { projectNo.value = v ? String(v) : '' })

const groups = computed(() => {
  const map = {}
  for (const [kind, list] of [['todo', d.value.todo], ['overdue', d.value.overdue], ['done', d.value.done]]) {
    for (const n of list) {
      const k = n.project_no
      if (!map[k]) map[k] = { project_no: k, project_name: n.project_name, project_status: n.project_status, todo: 0, overdue: 0, done: 0, nodes: [] }
      map[k][kind]++
      map[k].nodes.push(n)
    }
  }
  return Object.values(map)
})

function chipCls(n) {
  if (n.status === 'DONE') return 'done'
  if (n.plan_date && n.plan_date < new Date().toISOString().slice(0, 10)) return 'od'
  return 'date'
}
function key(t) { return t.project_no + t.node_code }
function fmt(dt) { return dt ? String(dt).slice(0, 10) : '—' }
function open(t) { router.push({ path: '/project/progress', query: { projectNo: t.project_no, node: t.node_code } }) }
function noop() {}

async function load() {
  const res = await myTodos()
  raw.value = { overdue: res.data?.overdue || [], todo: res.data?.todo || [], done: res.data?.done || [] }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 14px; margin-bottom: 12px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.proj-scope { margin-bottom: 12px; }
.sec { margin-bottom: 18px; }
.sec-head { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.sec-head.od { color: #f56c6c; }
.muted { color: #c0c4cc; font-size: 12px; }
.board { display: flex; gap: 12px; align-items: flex-start; }
.bcol { flex: 1; border: 1px solid #e4e7ed; border-radius: 10px; background: #f7f8fb; min-width: 220px; }
.bcol-head { padding: 8px 12px; font-size: 13px; font-weight: 600; background: #fff; border-radius: 10px 10px 0 0; border-bottom: 1px solid #e4e7ed; display: flex; justify-content: space-between; }
.bcol.od .bcol-head { color: #f56c6c; }
.bcol-body { padding: 8px; }
.item { padding: 7px 10px; background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 6px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.item:hover { border-color: var(--plm-accent, #409eff); }
.item .nm { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #303133; }
.item .nm b { color: #c0c4cc; margin: 0 5px; font-weight: 400; }
.item .dt { font-size: 11px; color: #909399; font-family: monospace; flex-shrink: 0; background: #f7f8fb; border-radius: 4px; padding: 1px 6px; }
.item .dt.od { color: #f56c6c; background: #fef0f0; }
.pgroup { border: 1px solid #e4e7ed; border-radius: 10px; padding: 10px 12px; margin-bottom: 10px; }
.pg-head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.pg-head .cnt { margin-left: auto; font-size: 12px; color: #909399; }
.pg-items { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.chip { font-size: 12px; padding: 2px 8px; border-radius: 12px; cursor: pointer; }
.chip.done { background: #f0f9eb; color: #67c23a; }
.chip.od { background: #fef0f0; color: #f56c6c; }
.chip.date { background: #ecf5ff; color: #409eff; }
</style>
