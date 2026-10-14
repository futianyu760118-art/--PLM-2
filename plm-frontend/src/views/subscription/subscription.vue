<template>
  <div class="page-container">
    <OperationGuide module-key="subscriptionSubscription" />

    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的订阅</span>
          <el-radio-group v-model="query.type" size="small" @change="search">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="ITEM">单条</el-radio-button>
            <el-radio-button value="PERIOD">周期</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="query.status" size="small" @change="search">
            <el-radio-button value="">全部状态</el-radio-button>
            <el-radio-button value="ENABLED">启用</el-radio-button>
            <el-radio-button value="PAUSED">暂停</el-radio-button>
          </el-radio-group>
          <span class="head-tip">
            周期订阅 {{ periodCount }} / {{ maxPeriod }} 条
          </span>
          <el-button type="primary" icon="Plus" @click="openCreate">新建订阅</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="subNo" label="订阅编号" width="180" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'ITEM' ? 'info' : 'primary'" effect="plain">
              {{ row.type === 'ITEM' ? '单条' : '周期' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="订阅名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="订阅对象 / 范围" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.type === 'ITEM'">
              <span class="mono">{{ row.ledgerNo || '—' }}</span>
              <span class="muted" style="margin-left:6px">{{ row.ledgerName || '' }}</span>
            </span>
            <span v-else>{{ row.filterText || '本部门全部资料' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="120">
          <template #default="{ row }">{{ periodLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="推送时点" width="100" align="center">
          <template #default="{ row }">{{ row.type === 'ITEM' ? '实时' : (row.pushTime || '08:00') }}</template>
        </el-table-column>
        <el-table-column label="下次生成" width="170">
          <template #default="{ row }">{{ row.type === 'ITEM' ? '—' : fmtTime(row.nextRunAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'warning'">
              {{ row.status === 'ENABLED' ? '启用' : '暂停' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" size="small"
              @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '暂停' : '恢复' }}
            </el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有订阅" :image-size="70">
            <el-button type="primary" @click="openCreate">新建订阅</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @size-change="load"
          @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="dlg.visible" :title="dlg.id ? '编辑订阅' : '新建订阅'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="订阅类型" required>
          <el-radio-group v-model="dlg.type" :disabled="!!dlg.id" @change="onTypeChange">
            <el-radio-button value="ITEM">单条（某条资料的变更）</el-radio-button>
            <el-radio-button value="PERIOD">周期（按时间的汇总报告）</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="订阅名称">
          <el-input v-model="dlg.name" maxlength="64" show-word-limit
            :placeholder="dlg.type === 'ITEM' ? '留空则取资料名称' : '如 每周试验报告汇总'" />
        </el-form-item>

        <!-- 单条订阅：选定一条资料，之后该条的任何变更都会推给你（AC-B3） -->
        <el-form-item v-if="dlg.type === 'ITEM'" label="订阅资料" required>
          <el-select v-model="dlg.ledgerId" filterable remote clearable placeholder="输入名称/编号搜索资料"
            :remote-method="searchLedger" :loading="ledgerLoading" style="width:100%">
            <el-option v-for="l in ledgerOptionsList" :key="l.ledgerId"
              :label="`${l.ledgerNo} ${l.name}`" :value="l.ledgerId" />
          </el-select>
        </el-form-item>

        <template v-else>
          <el-form-item label="生成周期" required>
            <el-select v-model="dlg.period" style="width:180px">
              <el-option label="按日（每天一份，覆盖前一天）" value="DAY" />
              <el-option label="按周（每周一份，覆盖上一周）" value="WEEK" />
              <el-option label="按月（每月一份，覆盖上一月）" value="MONTH" />
            </el-select>
            <span class="muted" style="margin-left:10px">
              报告覆盖的是上一个完整周期，如按日 8:00 生成昨天一整天的汇总
            </span>
          </el-form-item>

          <el-form-item v-if="dlg.period === 'WEEK'" label="星期几" required>
            <el-select v-model="dlg.weekday" style="width:180px">
              <el-option v-for="(w, i) in WEEKDAYS" :key="i" :label="w" :value="i + 1" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="dlg.period === 'MONTH'" label="每月几号" required>
            <el-input-number v-model="dlg.monthday" :min="1" :max="28" />
            <span class="muted" style="margin-left:10px">限定 1–28，保证每月都存在该日</span>
          </el-form-item>

          <el-form-item label="推送时点">
            <el-time-select v-model="dlg.pushTime" start="00:00" step="00:30" end="23:30"
              placeholder="选择时点" style="width:180px" />
          </el-form-item>

          <el-form-item label="资料范围">
            <div class="filter-box">
              <div class="muted" style="margin-bottom:6px">
                不选则覆盖本部门全部资料；同一维度内多选取「或」，跨维度取「与」
              </div>
              <div v-for="dim in filterDims" :key="dim.id" class="filter-row">
                <span class="filter-label">{{ dim.dimName }}</span>
                <el-select v-model="dlg.filterSel[dim.id]" multiple collapse-tags collapse-tags-tooltip
                  clearable placeholder="全部" style="flex:1">
                  <el-option v-for="v in dim.values" :key="v.id" :label="v.valueName" :value="v.id" />
                </el-select>
              </div>
              <el-empty v-if="!filterDims.length" description="本部门暂无可用的分类维度" :image-size="50" />
            </div>
          </el-form-item>
        </template>

        <el-form-item v-if="dlg.id" label="状态">
          <el-radio-group v-model="dlg.status">
            <el-radio-button value="ENABLED">启用</el-radio-button>
            <el-radio-button value="PAUSED">暂停</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OperationGuide from '@/components/OperationGuide.vue'
import {
  pageSubscription, createSubscription, updateSubscription, deleteSubscription,
  setSubscriptionStatus, categorySelect, pageRepository
} from '@/api/subscription'

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const maxPeriod = 20

const loading = ref(false)
const saving = ref(false)
const ledgerLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, type: '', status: '' })

const filterDims = ref([])
const ledgerOptionsList = ref([])
const dlg = reactive({
  visible: false, id: null, type: 'PERIOD', name: '', ledgerId: null,
  period: 'DAY', weekday: 1, monthday: 1, pushTime: '08:00', status: 'ENABLED', filterSel: {}
})

// 周期订阅条数上限提示（AC-B4-9），列表本身按页取，这里用当前页兜底展示
const periodCount = computed(() => tableData.value.filter(r => r.type === 'PERIOD').length)

async function load() {
  loading.value = true
  try {
    const r = await pageSubscription(query)
    tableData.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function fmtTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function periodLabel(row) {
  if (row.type === 'ITEM') return '实时变更'
  if (row.period === 'DAY') return '按日'
  if (row.period === 'WEEK') return '按周 · ' + (WEEKDAYS[(row.weekday || 1) - 1] || '')
  if (row.period === 'MONTH') return '按月 · 每月 ' + (row.monthday || 1) + ' 号'
  return '—'
}

function onTypeChange() {
  if (dlg.type === 'ITEM') dlg.filterSel = {}
}

async function searchLedger(kw) {
  if (!kw) return
  ledgerLoading.value = true
  try {
    const r = await pageRepository({ keyword: kw, pageNum: 1, pageSize: 20 })
    ledgerOptionsList.value = r.data?.records || []
  } finally {
    ledgerLoading.value = false
  }
}

async function ensureFilterDims() {
  if (filterDims.value.length) return
  const r = await categorySelect()
  filterDims.value = r.data || []
}

/** 分类条件序列化成后端 CategoryFilter：只保留真正选了值的维度 */
function buildFilter() {
  const dims = []
  for (const dim of filterDims.value) {
    const valueIds = dlg.filterSel[dim.id] || []
    if (valueIds.length) dims.push({ dimId: dim.id, valueIds })
  }
  return { dims }
}

async function openCreate() {
  Object.assign(dlg, {
    visible: true, id: null, type: 'PERIOD', name: '', ledgerId: null,
    period: 'DAY', weekday: 1, monthday: 1, pushTime: '08:00', status: 'ENABLED', filterSel: {}
  })
  ledgerOptionsList.value = []
  await ensureFilterDims()
}

async function openEdit(row) {
  Object.assign(dlg, {
    visible: true, id: row.id, type: row.type, name: row.name || '', ledgerId: row.ledgerId,
    period: row.period || 'DAY', weekday: row.weekday || 1, monthday: row.monthday || 1,
    pushTime: (row.pushTime || '08:00').slice(0, 5), status: row.status || 'ENABLED', filterSel: {}
  })
  ledgerOptionsList.value = row.ledgerId
    ? [{ ledgerId: row.ledgerId, ledgerNo: row.ledgerNo, name: row.ledgerName }]
    : []
  if (row.type === 'PERIOD') {
    await ensureFilterDims()
    for (const g of row.filter?.dims || []) {
      dlg.filterSel[g.dimId] = g.valueIds || []
    }
  }
}

async function save() {
  if (dlg.type === 'ITEM' && !dlg.ledgerId) {
    ElMessage.warning('请选择要订阅的资料')
    return
  }
  if (dlg.type === 'PERIOD' && dlg.period === 'MONTH' && !dlg.monthday) {
    ElMessage.warning('请选择每月几号')
    return
  }
  saving.value = true
  try {
    const payload = {
      type: dlg.type,
      name: dlg.name?.trim() || undefined,
      ledgerId: dlg.type === 'ITEM' ? dlg.ledgerId : undefined,
      period: dlg.type === 'PERIOD' ? dlg.period : undefined,
      weekday: dlg.type === 'PERIOD' && dlg.period === 'WEEK' ? dlg.weekday : undefined,
      monthday: dlg.type === 'PERIOD' && dlg.period === 'MONTH' ? dlg.monthday : undefined,
      pushTime: dlg.type === 'PERIOD' ? dlg.pushTime : undefined,
      filter: dlg.type === 'PERIOD' ? buildFilter() : undefined,
      status: dlg.id ? dlg.status : undefined
    }
    if (dlg.id) await updateSubscription(dlg.id, payload)
    else await createSubscription(payload)
    ElMessage.success('已保存')
    dlg.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 暂停保留历史报告，恢复后从下一个完整周期起生成（AC-B4-6） */
async function toggleStatus(row) {
  const next = row.status === 'ENABLED' ? 'PAUSED' : 'ENABLED'
  if (next === 'PAUSED') {
    await ElMessageBox.confirm(
      `暂停「${row.name}」后将不再生成新报告，已生成的报告不受影响。确定暂停？`, '暂停确认', { type: 'warning' })
  }
  await setSubscriptionStatus(row.id, next)
  ElMessage.success(next === 'ENABLED' ? '已恢复' : '已暂停')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `删除订阅「${row.name}」后不再生成新报告，已生成的历史报告仍可在「我的报告」里查看。确定删除？`,
    '删除确认', { type: 'warning', confirmButtonText: '确认删除' })
  await deleteSubscription(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style lang="scss" scoped>
.page-container { padding: 16px; }
.card-head { display: flex; align-items: center; gap: 12px; }
.head-tip { flex: 1; font-size: 12.5px; color: #909399; }
.filter-box { width: 100%; }
.filter-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.filter-label { width: 90px; flex: 0 0 90px; font-size: 13px; color: #606266; }
.pagination-wrap { margin-top: 12px; display: flex; justify-content: flex-end; }
.muted { color: #909399; font-size: 12px; }
.mono { font-family: monospace; }
</style>
