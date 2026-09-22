<template>
  <div class="page-container">
    <OperationGuide module-key="process" />
    <DataIOBar module="process" name="工序路线" />
    <el-card>
      <el-form :inline="true" :model="query">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="路线编号/名称/关联单号" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openCreate">新建工序路线</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="routeNo" label="路线编号" width="160" fixed />
        <el-table-column prop="routeName" label="工序路线" min-width="180" show-overflow-tooltip />
        <el-table-column label="关联业务" width="160">
          <template #default="{row}">
            <el-tag v-if="row.refType" size="small" type="info">{{ refLabel(row.refType) }}</el-tag>
            <span style="margin-left:6px">{{ row.refId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="routeTag(row.status)">{{ routeLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="completedAt" label="完成时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">工序详情</el-button>
            <el-button v-if="row.status==='RUNNING'" link type="danger" size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="createDialog.visible" title="新建工序路线" width="760px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="路线名称" required><el-input v-model="form.routeName" placeholder="如: 投光灯试制工序" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="业务类型">
              <el-select v-model="form.refType" style="width:100%">
                <el-option v-for="r in refTypes" :key="r.v" :label="r.l" :value="r.v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="关联单号"><el-input v-model="form.refId" placeholder="项目号/料号" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">工序步骤 (按顺序自动流转, 完成一道自动推送下一道)</el-divider>
        <div v-for="(s, i) in form.steps" :key="i" style="display:flex;gap:8px;align-items:center;margin-bottom:8px">
          <el-tag size="small" style="width:44px;justify-content:center">{{ i + 1 }}</el-tag>
          <el-input v-model="s.name" placeholder="工序名称, 如: 结构设计" style="flex:1" />
          <el-select v-model="s.ownerId" placeholder="负责人" filterable style="width:160px">
            <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
          <el-input-number v-model="s.slaHours" :min="0" :step="8" placeholder="SLA" style="width:110px" />
          <span style="font-size:12px;color:#999">小时</span>
          <el-button link type="danger" icon="Delete" :disabled="form.steps.length <= 1" @click="removeStep(i)" />
        </div>
        <el-button type="primary" plain icon="Plus" size="small" @click="addStep">添加工序</el-button>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建并启动</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer.visible" :title="'工序流转 - ' + (detail.route?.routeName || '')" size="720px">
      <template v-if="detail.route">
        <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="路线编号">{{ detail.route.routeNo }}</el-descriptions-item>
          <el-descriptions-item label="关联业务">{{ refLabel(detail.route.refType) }} {{ detail.route.refId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="routeTag(detail.route.status)">{{ routeLabel(detail.route.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-steps :active="activeIndex" align-center>
          <el-step v-for="s in detail.steps" :key="s.id" :title="s.name"
            :status="stepStatus(s)" :description="stepDesc(s)" />
        </el-steps>

        <div v-if="currentStep" style="margin:18px 0;text-align:center">
          <el-button type="success" icon="CircleCheck" @click="handleComplete">完成「{{ currentStep.name }}」并推送下一道</el-button>
          <el-button type="warning" plain icon="Remove" @click="handleSkip">跳过本道</el-button>
        </div>

        <el-divider>流转记录</el-divider>
        <el-timeline>
          <el-timeline-item v-for="s in [...detail.steps].reverse()" :key="s.id" :timestamp="s.completedAt || s.startedAt || ''" placement="top"
            :type="s.status==='DONE'?'success':s.status==='ACTIVE'?'primary':s.status==='SKIPPED'?'warning':''">
            <b>第{{ s.stepOrder }}道 · {{ s.name }}</b> — {{ stepLabel(s.status) }}
            <p v-if="s.remark" style="color:#666">{{ s.remark }}</p>
            <p style="color:#999;font-size:12px">
              负责人: {{ ownerName(s.ownerId) }}
              <template v-if="s.slaHours"> · SLA {{ s.slaHours }}h</template>
              <template v-if="s.workItemId"> · 待办 #{{ s.workItemId }}</template>
            </p>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoutes, getRoute, createRoute, completeStep, skipStep, cancelRoute } from '@/api/process'
import { pageUser } from '@/api/system'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const users = ref([])

const refTypes = [
  { v: 'PROJECT', l: '项目NPI' }, { v: 'MOLD', l: '模具' },
  { v: 'MATERIAL', l: '物料料号' }, { v: 'ECN', l: 'ECN变更' }, { v: 'OTHER', l: '其他' }
]
const refLabel = (t) => ({ PROJECT: '项目', MOLD: '模具', MATERIAL: '物料', ECN: 'ECN', OTHER: '其他' }[t] || '-')
const routeLabel = (s) => ({ RUNNING: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s)
const routeTag = (s) => ({ RUNNING: 'primary', COMPLETED: 'success', CANCELLED: 'info' }[s] || 'info')
const stepLabel = (s) => ({ PENDING: '待流转', ACTIVE: '进行中', DONE: '已完成', SKIPPED: '已跳过' }[s] || s)
const ownerName = (id) => { const u = users.value.find(x => x.id === id); return u ? (u.realName || u.username) : (id || '未指派') }

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const createDialog = reactive({ visible: false })
const form = reactive({ routeName: '', refType: 'PROJECT', refId: '', steps: [] })
const detailDrawer = reactive({ visible: false })
const detail = ref({ route: null, steps: [], doneCount: 0, totalCount: 0 })

const currentStep = computed(() => detail.value.steps.find(s => s.status === 'ACTIVE'))
const activeIndex = computed(() => {
  const idx = detail.value.steps.findIndex(s => s.status === 'ACTIVE')
  return idx >= 0 ? idx : detail.value.steps.length
})
const stepStatus = (s) => s.status === 'DONE' ? 'success' : s.status === 'ACTIVE' ? 'process' : s.status === 'SKIPPED' ? 'error' : 'wait'
const stepDesc = (s) => stepLabel(s.status) + (s.completedAt ? ` · ${String(s.completedAt).slice(5, 16)}` : '')

async function loadData() {
  loading.value = true
  try {
    const res = await listRoutes(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetQuery() { Object.assign(query, { pageNum: 1, keyword: '', status: '' }); loadData() }

async function loadUsers() {
  const res = await pageUser({ pageNum: 1, pageSize: 200 })
  users.value = res.data.records || []
}

function addStep() { form.steps.push({ name: '', ownerId: null, slaHours: 24 }) }
function removeStep(i) { form.steps.splice(i, 1) }

async function openCreate() {
  Object.assign(form, { routeName: '', refType: 'PROJECT', refId: '', steps: [] })
  addStep()
  if (!users.value.length) await loadUsers()
  createDialog.visible = true
}

async function submitCreate() {
  if (!form.routeName) { ElMessage.warning('请输入路线名称'); return }
  const steps = form.steps.filter(s => s.name && s.name.trim())
  if (!steps.length) { ElMessage.warning('至少需要一道工序'); return }
  submitting.value = true
  try {
    await createRoute({ routeName: form.routeName, refType: form.refType, refId: form.refId, steps })
    ElMessage.success('工序路线已创建, 第一道工序待办已推送')
    createDialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function openDetail(row) {
  detailDrawer.visible = true
  if (!users.value.length) await loadUsers()
  const res = await getRoute(row.id)
  detail.value = res.data
}

async function refreshDetail() {
  const res = await getRoute(detail.value.route.id)
  detail.value = res.data
}

async function handleComplete() {
  const s = currentStep.value
  const { value } = await ElMessageBox.prompt(`确认完成「${s.name}」? 将自动推送下一道工序。`, '完成工序', {
    confirmButtonText: '完成', inputValue: '', inputPlaceholder: '完成备注(可选)'
  }).catch(() => ({ value: null }))
  if (value === null) return
  await completeStep(s.id, value)
  ElMessage.success('已完成, 下一道工序待办已自动推送')
  refreshDetail(); loadData()
}

async function handleSkip() {
  const s = currentStep.value
  const { value } = await ElMessageBox.prompt(`跳过「${s.name}」? 跳过将留痕并推送下一道。`, '跳过工序', {
    confirmButtonText: '跳过', inputValue: '', inputPlaceholder: '跳过原因', type: 'warning'
  }).catch(() => ({ value: null }))
  if (value === null) return
  await skipStep(s.id, value)
  ElMessage.success('已跳过, 下一道工序待办已自动推送')
  refreshDetail(); loadData()
}

async function handleCancel(row) {
  await ElMessageBox.confirm(`取消工序路线 [${row.routeNo}]? 当前工序待办将一并关闭。`, '警告', { type: 'warning' })
  await cancelRoute(row.id)
  ElMessage.success('已取消')
  loadData()
}

onMounted(loadData)
</script>
