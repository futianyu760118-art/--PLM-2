<template>
  <div class="page-container">
    <el-card>
      <div class="legend">
        研发项目跟踪总表（一行一项目 × 22 节点）　
        <el-tag size="small" type="success" effect="plain">绿底 = 已完成(显示实际完成日期)</el-tag>
        <el-tag size="small" type="primary" effect="plain">蓝 = 计划时间</el-tag>
        <el-tag size="small" type="danger" effect="plain">红 = 已逾期</el-tag>
        <el-tag size="small" type="danger" effect="plain">X = 未完成</el-tag>
        <el-tag size="small" type="warning" effect="plain">进行中</el-tag>
        <el-tag size="small" type="info" effect="plain">待定</el-tag>
        <span class="tip">点击单元格即可编辑</span>
      </div>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="编号/名称/客户/负责人" clearable style="width:200px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="query.status" clearable style="width:130px">
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已量产" value="MP" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="暂停" value="ON_HOLD" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点状态">
          <el-select v-model="query.nodeStatus" clearable style="width:150px">
            <el-option label="有进行中节点" value="in_progress" />
            <el-option label="有计划日期" value="has_date" />
            <el-option label="有未设置节点" value="incomplete" />
            <el-option label="关键节点全完成" value="all_done" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="loadData">刷新</el-button>
          <el-button type="success" plain icon="MagicStick" :loading="syncing" @click="doRollup">同步台账(完成判定)</el-button>
          <el-button icon="Grid" @click="openMap">节点↔工作表对照</el-button>
        </el-form-item>
        <el-form-item label="每页">
          <el-select v-model="query.pageSize" style="width:90px" @change="loadData">
            <el-option v-for="n in [10,20,50,100]" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe size="small" height="calc(100vh - 320px)">
        <el-table-column prop="projectNo" label="项目编号" width="150" fixed sortable />
        <el-table-column prop="projectName" label="项目名称" width="170" show-overflow-tooltip fixed />
        <el-table-column prop="owner" label="负责人" width="80" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{row}">
            <el-select :model-value="row.status" size="small" style="width:96px" @change="v => changeStatus(row, v)">
              <el-option v-for="o in STATUS_OPTS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-for="col in nodeDefs" :key="col.code" :label="col.label" width="92" align="center">
          <template #header>
            <span :style="{ color: col.key ? '#f56c6c' : '' }">{{ col.label }}<b v-if="col.key"> ★</b></span>
          </template>
          <template #default="{row}">
            <el-select v-if="isEditing(row.id, col.code)" v-model="editValue" size="small" style="width:82px"
              filterable allow-create default-first-option @change="saveCell(row, col.code)">
              <el-option label="—" value="" />
              <el-option label="V" value="V" />
              <el-option label="X" value="X" />
              <el-option label="进行中" value="进行中" />
              <el-option label="待定" value="待定" />
              <el-option label="待进行" value="待进行" />
            </el-select>
            <span v-else class="cell" :class="[cellClass(row, col.code), flashCells[row.id + ':' + col.code] ? 'c-flash' : '']" @click="startEdit(row, col.code)">
              {{ row.cells[col.code] || '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="完成" width="80" align="center" fixed="right">
          <template #default="{row}">{{ row.nodeDone }}/{{ row.nodeTotal }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openProgress(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="mapVisible" title="项目明细表节点 ↔ 模板工作表 对照" width="900px" top="6vh">
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        title="填写模板工作表 → 统计到项目明细表(22节点) → 汇总到研发项目跟踪" />
      <el-table :data="mapRows" border stripe size="small" max-height="520">
        <el-table-column prop="seq" label="#" width="46" align="center" />
        <el-table-column prop="nodeName" label="节点" width="100">
          <template #default="{row}"><span :style="{color: row.isKey ? '#f56c6c' : ''}">{{ row.nodeName }}<b v-if="row.isKey"> ★</b></span></template>
        </el-table-column>
        <el-table-column prop="worksheetKeys" label="对应模板工作表" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sourceTables" label="系统载体表" min-width="200" show-overflow-tooltip />
        <el-table-column prop="doneRule" label="完成判定" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTrackingNodeDefs, pageTracking, saveTrackingCell, getNodeSheetMap, rollupAll, updateProject } from '@/api/project'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const nodeDefs = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', nodeStatus: '' })
const editing = reactive({ rowId: null, code: '' })
const editValue = ref('')
const mapVisible = ref(false)
const mapRows = ref([])
const syncing = ref(false)

const statusLabel = (s) => ({ ACTIVE: '进行中', MP: '已量产', CLOSED: '已关闭', ON_HOLD: '暂停', CANCELLED: '取消' }[s] || s || '')
const statusTag = (s) => ({ ACTIVE: 'primary', MP: 'success', CLOSED: 'info', ON_HOLD: 'warning', CANCELLED: 'danger' }[s] || 'info')
const STATUS_OPTS = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'MP', label: '已量产' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'ON_HOLD', label: '暂停' },
  { value: 'CANCELLED', label: '取消' }
]

async function changeStatus(row, status) {
  try {
    await updateProject({ id: row.id, status })
    row.status = status
    ElMessage.success('项目状态已更新')
  } catch (e) { /* 拦截器已提示 */ }
}
const cellClass = (row, code) => {
  const v = row.cells[code]
  if (row.doneCells && row.doneCells[code]) return 'c-done'
  if (row.overdueCells && row.overdueCells[code]) return 'c-overdue'
  if (v === 'X') return 'c-fail'
  if (v === '进行中') return 'c-ing'
  if (v === '待定') return 'c-hold'
  if (/^\d{4}-\d{2}-\d{2}/.test(v || '')) return 'c-date'
  return 'c-empty'
}

async function loadDefs() {
  const res = await getTrackingNodeDefs()
  nodeDefs.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageTracking(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function isEditing(rowId, code) { return editing.rowId === rowId && editing.code === code }
function startEdit(row, code) {
  editing.rowId = row.id; editing.code = code; editValue.value = row.cells[code] || ''
}
function cancelEdit() { editing.rowId = null; editing.code = '' }

// —— Skill 1 完成反馈：单元格保存后短暂“弹一下” ——
const flashCells = reactive({})
function flash(row, code) {
  const key = row.id + ':' + code
  flashCells[key] = true
  setTimeout(() => { delete flashCells[key] }, 700)
}

// —— Skill 2 勾选联动：一处改动，计数与颜色两处同步 ——
function refreshRowDone(row) {
  let done = 0
  for (const c of Object.keys(row.cells)) if (row.doneCells && row.doneCells[c]) done++
  row.nodeDone = done
}
function applyLocalCell(row, code, val) {
  row.cells[code] = val
  row.doneCells = row.doneCells || {}
  row.overdueCells = row.overdueCells || {}
  const today = new Date().toISOString().slice(0, 10)
  const isDate = /^\d{4}-\d{2}-\d{2}/.test(val || '')
  if (val === 'V' || val === '√' || val === '完成') {
    row.doneCells[code] = true
    delete row.overdueCells[code]
  } else {
    delete row.doneCells[code]
    if (isDate && (val || '') < today) row.overdueCells[code] = true
    else delete row.overdueCells[code]
  }
  refreshRowDone(row)
}

async function saveCell(row, code) {
  const val = editValue.value
  if (val === (row.cells[code] || '')) { cancelEdit(); return }
  try {
    await saveTrackingCell(row.id, { nodeCode: code, value: val })
    applyLocalCell(row, code, val)
    flash(row, code)
  } catch (e) { /* 拦截器已提示 */ }
  cancelEdit()
}

function openProgress(row) {
  router.push({ path: '/project/progress', query: { project: row.id } })
}

async function doRollup() {
  syncing.value = true
  try {
    const res = await rollupAll()
    ElMessage.success(`完成判定: 更新 ${res.data.updated} 个节点, 涉及 ${res.data.projectsTouched} 个项目`)
    loadData()
  } finally { syncing.value = false }
}

async function openMap() {
  mapVisible.value = true
  if (!mapRows.value.length) {
    const res = await getNodeSheetMap()
    mapRows.value = res.data || []
  }
}

onActivated(async () => {
  // 由证据台账「前往」跳入时按关键字预置筛选(AC-A4-2)
  const kw = route.query.keyword
  if (kw) query.keyword = String(kw)
  if (!nodeDefs.value.length) await loadDefs()
  loadData()
})
</script>

<style scoped>
.legend { margin-bottom: 10px; color: #666; font-size: 13px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.legend .tip { margin-left: 8px; color: #999; font-size: 12px; }
.filter-bar .el-button { transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1); }
.filter-bar .el-button:active { transform: scale(0.95); }
.cell { display: inline-block; min-width: 60px; cursor: pointer; padding: 1px 4px; border-radius: 3px;
  transition: transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1), color 0.2s ease, background-color 0.2s ease; }
.cell:hover { background: #ecf5ff; }
.cell:active { transform: scale(0.9); }
.c-flash { animation: cellPop 0.4s cubic-bezier(0.3, 0.7, 0.4, 1.4); }
@keyframes cellPop { 0% { transform: scale(0.7); opacity: 0.5; } 60% { transform: scale(1.1); } 100% { transform: scale(1); opacity: 1; } }
.c-done { background: #f0f9eb; color: #67c23a; font-weight: 600; }
.c-fail { color: #f56c6c; font-weight: 600; }
.c-ing { color: #e6a23c; }
.c-hold { color: #909399; }
.c-date { color: #409eff; }
.c-overdue { color: #f56c6c; font-weight: 600; }
.c-empty { color: #c0c4cc; }
</style>
