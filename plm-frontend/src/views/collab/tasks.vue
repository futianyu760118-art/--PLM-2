<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>{{ activeTab === 'dept' ? '部门待办' : '我的待办' }}</h3>
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button value="self">我的待办</el-radio-button>
          <el-radio-button value="dept">部门待办</el-radio-button>
        </el-radio-group>
        <el-checkbox v-model="onlyActive" @change="noop">仅进行中项目</el-checkbox>
        <el-input v-model="todoKeyword" placeholder="查找: 节点/项目/成员" clearable size="small" style="width:200px" prefix-icon="Search" />
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
          <el-radio-button value="group">按项目分组</el-radio-button>
        </el-radio-group>
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </div>

      <el-alert v-if="activeTab === 'self' && projectNo" type="info" :closable="false" class="proj-scope">
        仅显示项目 <b>{{ projectNo }}</b>
        <el-button link type="primary" size="small" style="margin-left:10px" @click="clearProject">返回全部待办</el-button>
      </el-alert>
      <el-alert v-if="activeTab === 'dept' && scope === 'SELF'" type="info" :closable="false" style="margin-bottom:12px">
        无管理范围，仅显示个人待办（如需查看部门，请联系管理员配置经理角色）
      </el-alert>
      <el-alert v-else-if="activeTab === 'dept' && scope === 'DEPT'" type="success" :closable="false" style="margin-bottom:12px">
        当前可查看本部门成员的待办
      </el-alert>

      <!-- 看板：三列横排 -->
      <div v-if="viewMode === 'board'" class="board">
        <div class="bcol od"><div class="bcol-head">逾期 <b>{{ activeData.overdue.length }}</b></div>
          <div v-for="t in activeData.overdue" :key="key(t)" class="item" @click="open(t)">
            <span class="nm" :title="`${t.project_no} ${t.project_name || ''}`"><template v-if="isDept">{{ t.owner_name }}<b>·</b></template>{{ t.project_no }}<i v-if="t.project_name" class="pn">{{ t.project_name }}</i><b>·</b>{{ t.node_name }}</span>
            <span class="dt od">{{ fmt(t.plan_date) }}</span>
          </div>
          <p v-if="!activeData.overdue.length" class="muted">无</p>
        </div>
        <div class="bcol"><div class="bcol-head">待办 <b>{{ activeData.todo.length }}</b></div>
          <div v-for="t in activeData.todo" :key="key(t)" class="item" @click="open(t)">
            <span class="nm" :title="`${t.project_no} ${t.project_name || ''}`"><template v-if="isDept">{{ t.owner_name }}<b>·</b></template>{{ t.project_no }}<i v-if="t.project_name" class="pn">{{ t.project_name }}</i><b>·</b>{{ t.node_name }}</span>
            <span class="dt">{{ t.plan_date ? fmt(t.plan_date) : '无到期' }}</span>
          </div>
          <p v-if="!activeData.todo.length" class="muted">无</p>
        </div>
        <div class="bcol"><div class="bcol-head">已完成 <b>{{ activeData.done.length }}</b></div>
          <div v-for="t in activeData.done" :key="key(t)" class="item" @click="open(t)">
            <span class="nm" :title="`${t.project_no} ${t.project_name || ''}`"><template v-if="isDept">{{ t.owner_name }}<b>·</b></template>{{ t.project_no }}<i v-if="t.project_name" class="pn">{{ t.project_name }}</i><b>·</b>{{ t.node_name }}</span>
            <span class="dt">{{ t.actual_date ? fmt(t.actual_date) : '—' }}</span>
          </div>
          <p v-if="!activeData.done.length" class="muted">无</p>
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
            <span v-for="n in g.nodes" :key="key(n)" class="chip" :class="chipCls(n)" @click="open(n)">
              <template v-if="isDept">{{ n.owner_name }} · </template>{{ n.node_name }}
            </span>
          </div>
        </div>
        <el-empty v-if="!groups.length" description="无进行中/匹配项目" :image-size="60" />
      </div>

      <!-- 列表：三块纵向同页 -->
      <TodoSections v-else :data="activeData" :show-owner="isDept" @open="open" @save="onTodoSave" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { myTodos, deptTodos } from '@/api/me'
import { pageProject } from '@/api/project'
import { updateNode } from '@/api/progress'
import TodoSections from '@/components/collab/TodoSections.vue'

const router = useRouter()
const route = useRoute()
const raw = ref({ overdue: [], todo: [], done: [] })
const viewMode = ref('list')
const onlyActive = ref(true)
const projectNo = ref(route.query.projectNo ? String(route.query.projectNo) : '')
const activeTab = ref('self')
const deptList = ref([])
const scope = ref('')
const selfTodo = ref({ overdue: [], todo: [], done: [], realName: '' })

const statusLabel = (s) => ({ ACTIVE: '进行中', MP: '已量产', CLOSED: '已关闭', ON_HOLD: '暂停' }[s] || s)
const tag = (s) => ({ ACTIVE: 'primary', MP: 'success', CLOSED: 'info', ON_HOLD: 'warning' }[s] || 'info')

const isDept = computed(() => activeTab.value === 'dept')
const onlyActiveFilter = (l) => l.filter((t) => t.project_status === 'ACTIVE')

const filterList = (list) => (projectNo.value ? list.filter((t) => t.project_no === projectNo.value) : list)

// 我的待办：默认仅进行中项目；关闭开关则全部；再按项目过滤
const d = computed(() => {
  const base = onlyActive.value
    ? { overdue: onlyActiveFilter(raw.value.overdue), todo: onlyActiveFilter(raw.value.todo), done: onlyActiveFilter(raw.value.done) }
    : raw.value
  return { overdue: filterList(base.overdue), todo: filterList(base.todo), done: filterList(base.done) }
})

// 部门待办：把部门内各成员待办打平成与个人待办一致的三段结构，并标注成员名
const deptFlat = computed(() => {
  const items = { overdue: [], todo: [], done: [] }
  if (deptList.value.length) {
    const seen = new Set()
    for (const d of deptList.value) {
      for (const m of d.members) {
        for (const kind of ['overdue', 'todo', 'done']) {
          for (const n of (m[kind] || [])) {
            const key = m.userId + ':' + n.project_no + ':' + n.node_code
            if (seen.has(key)) continue
            seen.add(key)
            items[kind].push({ ...n, owner_name: m.realName })
          }
        }
      }
    }
  } else {
    for (const kind of ['overdue', 'todo', 'done']) {
      for (const n of (selfTodo.value[kind] || [])) {
        items[kind].push({ ...n, owner_name: selfTodo.value.realName || '' })
      }
    }
  }
  return items
})

// 当前页签数据源：部门待办同样应用「仅进行中项目」过滤 + 关键字查找
const todoKeyword = ref('')
const kwFilter = (l) => {
  const kw = todoKeyword.value.trim().toLowerCase()
  if (!kw) return l
  return l.filter(t =>
    (t.node_name || '').toLowerCase().includes(kw) ||
    (t.project_no || '').toLowerCase().includes(kw) ||
    (t.project_name || '').toLowerCase().includes(kw) ||
    (t.owner_name || '').toLowerCase().includes(kw))
}
const activeData = computed(() => {
  let base
  if (!isDept.value) base = d.value
  else if (!onlyActive.value) base = deptFlat.value
  else base = { overdue: onlyActiveFilter(deptFlat.value.overdue), todo: onlyActiveFilter(deptFlat.value.todo), done: onlyActiveFilter(deptFlat.value.done) }
  return { overdue: kwFilter(base.overdue), todo: kwFilter(base.todo), done: kwFilter(base.done) }
})

// ===== 行内编辑保存：按 project_no 映射 project_id 后调 updateNode =====
const pidMap = ref({})
async function loadPidMap() {
  try {
    const r = await pageProject({ pageNum: 1, pageSize: 1000 })
    pidMap.value = {}
    for (const p of (r.data.records || [])) pidMap.value[p.projectNo] = p.id
  } catch (e) { /* 忽略 */ }
}
const savingTodo = ref(false)
async function onTodoSave(row, patch) {
  const pid = pidMap.value[row.project_no]
  if (!pid) { ElMessage.warning('未找到项目，无法保存'); return }
  savingTodo.value = true
  try {
    await updateNode(pid, row.node_code, {
      plan_date: 'plan_date' in patch ? patch.plan_date : undefined,
      clearPlan: 'plan_date' in patch && !patch.plan_date,
      actual_date: 'actual_date' in patch ? patch.actual_date : undefined,
      clearActual: 'actual_date' in patch && !patch.actual_date
    })
    ElMessage.success('已保存')
    // 本地立即更新 + 后台刷新
    if ('plan_date' in patch) row.plan_date = patch.plan_date
    if ('actual_date' in patch) row.actual_date = patch.actual_date
    refresh()
  } finally { savingTodo.value = false }
}

function clearProject() {
  projectNo.value = ''
  router.replace({ path: '/project/tasks' })
}
watch(() => route.query.projectNo, (v) => { projectNo.value = v ? String(v) : '' })

const groups = computed(() => {
  const map = {}
  for (const [kind, list] of [['todo', activeData.value.todo], ['overdue', activeData.value.overdue], ['done', activeData.value.done]]) {
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
function key(t) { return [t.owner_name, t.project_no, t.node_code].join(':') }
function fmt(dt) { return dt ? String(dt).slice(0, 10) : '—' }
function open(t) { router.push({ path: '/project/progress', query: { projectNo: t.project_no, node: t.node_code } }) }
function noop() {}

async function load() {
  const res = await myTodos()
  raw.value = { overdue: res.data?.overdue || [], todo: res.data?.todo || [], done: res.data?.done || [] }
}
async function loadDept() {
  const res = await deptTodos()
  deptList.value = res.data?.departments || []
  scope.value = res.data?.scope || 'SELF'
  const s = res.data?.self || {}
  selfTodo.value = { overdue: s.overdue || [], todo: s.todo || [], done: s.done || [], realName: s.realName || '' }
}
function refresh() { isDept.value ? loadDept() : load() }

watch(activeTab, (v) => { if (v === 'dept') loadDept() })
onMounted(() => { load(); loadPidMap() })
// keep-alive 缓存：切回本页时刷新最新节点状态
onActivated(() => { load(); if (activeTab.value === 'dept') loadDept() })
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
.item .nm i.pn { font-style: normal; color: #909399; font-size: 11px; margin-right: 2px; }
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
