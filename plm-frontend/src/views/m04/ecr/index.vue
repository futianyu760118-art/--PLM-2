<template>
  <div class="ecr-page">
    <div class="page-head">
      <div>
        <h2>ECR 工程变更申请</h2>
        <p class="sub">变更申请的提出与审批。审批通过后转 ECN 进入执行（升版 / 作废旧图 / 通知）。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        <el-button v-if="userStore.hasPermission('ecr:add')" type="primary" :icon="Plus" @click="openCreate">新建 ECR</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.prevent="onSearch">
        <el-form-item label="单号">
          <el-input v-model="query.ecrNo" placeholder="ECR 单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="料号">
          <el-input v-model="query.partNo" placeholder="关联料号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="(v, k) in CHANGE_TYPE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="(v, k) in STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe size="small" empty-text="暂无 ECR">
        <el-table-column prop="ecrNo" label="ECR 单号" width="170" />
        <el-table-column prop="title" label="变更主题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="partNo" label="关联料号" width="170" show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目号" width="150" show-overflow-tooltip />
        <el-table-column label="变更类型" width="100" align="center">
          <template #default="{ row }">{{ CHANGE_TYPE[row.changeType] || row.changeType || '-' }}</template>
        </el-table-column>
        <el-table-column label="紧急度" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.urgency" size="small" :type="URGENCY[row.urgency]?.type || 'info'" effect="plain">
              {{ URGENCY[row.urgency]?.label || row.urgency }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS[row.status]?.type || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">{{ fmt(row.applyTime || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="ecnNo" label="对应 ECN" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openProgress(row)">进度</el-button>
            <el-button
              v-if="canEdit(row) && userStore.hasPermission('ecr:edit')"
              link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canSubmit(row) && userStore.hasPermission('ecr:edit')"
              link type="warning" size="small" @click="doSubmit(row)">提交</el-button>
            <el-button
              v-if="row.status === 'REVIEWING'"
              link type="warning" size="small" @click="openApprove(row)">审批</el-button>
            <el-button
              v-if="row.status === 'APPROVED' && userStore.hasPermission('ecr:edit')"
              link type="success" size="small" @click="doToEcn(row)">转 ECN</el-button>
            <el-button
              v-if="row.status === 'DRAFT' && userStore.hasPermission('ecr:edit')"
              link type="danger" size="small" @click="doDelete(row)">删除</el-button>
            <el-button
              v-if="row.status !== 'CONVERTED' && row.status !== 'CLOSED' && userStore.hasPermission('ecr:edit')"
              link size="small" @click="doClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData" />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑 ECR' : '新建 ECR'" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="变更主题" prop="title">
              <el-input v-model="form.title" placeholder="一句话说清要改什么" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="变更类型" prop="changeType">
              <el-select v-model="form.changeType" style="width: 100%">
                <el-option v-for="(v, k) in CHANGE_TYPE" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联料号" prop="partNo">
              <el-input v-model="form.partNo" placeholder="转 ECN 时必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目号">
              <el-input v-model="form.projectNo" placeholder="如 HJ.26044.R1.1008" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急度">
              <el-select v-model="form.urgency" style="width: 100%">
                <el-option v-for="(v, k) in URGENCY" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="变更原因" prop="changeReason">
          <el-input v-model="form.changeReason" type="textarea" :rows="2" placeholder="客户要求 / 成本 / 品质 / 法规…" />
        </el-form-item>
        <el-form-item label="变更内容">
          <el-input v-model="form.changeContent" type="textarea" :rows="3" placeholder="改哪里、从什么改成什么" />
        </el-form-item>
        <el-form-item label="影响评估">
          <el-input v-model="form.impactAssessment" type="textarea" :rows="3" placeholder="影响的 BOM / 模具 / 工艺 / 在途订单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审批 -->
    <el-dialog v-model="approveVisible" title="ECR 审批" width="520px" destroy-on-close>
      <el-descriptions :column="1" size="small" border style="margin-bottom: 14px">
        <el-descriptions-item label="单号">{{ current.ecrNo }}</el-descriptions-item>
        <el-descriptions-item label="主题">{{ current.title }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item label="动作">
          <el-radio-group v-model="approveForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REJECT">驳回</el-radio-button>
            <el-radio-button value="BACK">退回上一节点</el-radio-button>
            <el-radio-button value="TERMINATE">终止</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="approveForm.comment" type="textarea" :rows="3" placeholder="通过可留空，其余动作必须填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>

    <!-- 进度 -->
    <el-drawer v-model="progressVisible" title="审批进度" size="480px" destroy-on-close>
      <div v-loading="progressLoading">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="单号">{{ current.ecrNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag size="small" :type="STATUS[progress.status]?.type || 'info'">
              {{ STATUS[progress.status]?.label || progress.status || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发起人">{{ progress.startedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmt(progress.startedAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec-title">节点</h4>
        <el-timeline v-if="progress.nodes && progress.nodes.length">
          <el-timeline-item
            v-for="n in progress.nodes"
            :key="n.id"
            :type="nodeType(n.seq)"
            :hollow="n.seq > (progress.currentSeq || 0)"
            :timestamp="'第 ' + n.seq + ' 节'">
            <div class="node-title">
              {{ n.nodeName }}
              <el-tag v-if="n.seq === progress.currentSeq && progress.status === 'RUNNING'" size="small" type="warning">进行中</el-tag>
            </div>
            <div class="node-meta">审批人：{{ approverLabel(n) }} · 限时 {{ n.timeoutHours || '-' }}h</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="尚无流程实例（未提交审批）" :image-size="70" />

        <h4 class="sec-title">动作流水</h4>
        <el-table v-if="progress.tasks && progress.tasks.length" :data="progress.tasks" size="small" border>
          <el-table-column prop="nodeSeq" label="节点" width="60" align="center" />
          <el-table-column prop="approver" label="处理人" width="110" />
          <el-table-column prop="action" label="动作" width="90" />
          <el-table-column prop="comment" label="意见" min-width="120" show-overflow-tooltip />
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.actedAt || row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无动作" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  pageEcr, addEcr, updateEcr, deleteEcr, submitEcr, approveEcr,
  getEcrProgress, toEcn, closeEcr
} from '@/api/ecr'

const userStore = useUserStore()

const CHANGE_TYPE = {
  MATERIAL: '物料变更',
  STRUCTURE: '结构变更',
  PROCESS: '工艺变更',
  DOCUMENT: '文档变更',
  BOM: 'BOM 变更'
}

const STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  REVIEWING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CONVERTED: { label: '已转 ECN', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'info' }
}

const URGENCY = {
  URGENT: { label: '紧急', type: 'danger' },
  NORMAL: { label: '普通', type: 'primary' },
  PLAN: { label: '计划', type: 'info' }
}

const query = reactive({ pageNum: 1, pageSize: 10, ecrNo: '', partNo: '', changeType: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const formVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = reactive({
  id: null, title: '', partNo: '', projectNo: '',
  changeType: 'STRUCTURE', changeReason: '', changeContent: '',
  impactAssessment: '', urgency: 'NORMAL'
})

const rules = {
  title: [{ required: true, message: '请填写变更主题', trigger: 'blur' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  changeReason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
}

const approveVisible = ref(false)
const approveForm = reactive({ action: 'APPROVE', comment: '' })
const current = ref({})

const progressVisible = ref(false)
const progressLoading = ref(false)
const progress = ref({})

function fmt(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

function canEdit(row) {
  return row.status === 'DRAFT' || row.status === 'REJECTED'
}

function canSubmit(row) {
  return row.status === 'DRAFT' || row.status === 'REJECTED'
}

function approverLabel(n) {
  const map = { ROLE: '角色', USER: '指定人', POSITION: '岗位', PROJECT_ROLE: '项目角色', DEPT_LEADER: '部门主管' }
  return `${map[n.approverType] || n.approverType || '-'}：${n.approverValue || '-'}`
}

function nodeType(seq) {
  const cur = progress.value.currentSeq || 0
  if (seq < cur) return 'success'
  if (seq === cur) return 'warning'
  return 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageEcr({ ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.pageNum = 1
  query.ecrNo = ''
  query.partNo = ''
  query.changeType = ''
  query.status = ''
  loadData()
}

function resetForm() {
  Object.assign(form, {
    id: null, title: '', partNo: '', projectNo: '',
    changeType: 'STRUCTURE', changeReason: '', changeContent: '',
    impactAssessment: '', urgency: 'NORMAL'
  })
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    title: row.title,
    partNo: row.partNo,
    projectNo: row.projectNo,
    changeType: row.changeType,
    changeReason: row.changeReason,
    changeContent: row.changeContent,
    impactAssessment: row.impactAssessment,
    urgency: row.urgency || 'NORMAL'
  })
  formVisible.value = true
}

async function saveForm() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateEcr({ ...form })
    } else {
      await addEcr({ ...form })
    }
    ElMessage.success('已保存')
    formVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function doSubmit(row) {
  await ElMessageBox.confirm(`确认提交 ${row.ecrNo} 进入审批？提交后不可编辑。`, '提交确认', { type: 'warning' })
  await submitEcr(row.id)
  ElMessage.success('已提交审批')
  loadData()
}

function openApprove(row) {
  current.value = { ...row }
  approveForm.action = 'APPROVE'
  approveForm.comment = ''
  approveVisible.value = true
}

async function submitApprove() {
  if (approveForm.action !== 'APPROVE' && !approveForm.comment.trim()) {
    ElMessage.warning('非通过动作必须填写意见')
    return
  }
  saving.value = true
  try {
    await approveEcr(current.value.id, { ...approveForm })
    ElMessage.success('已处理')
    approveVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function openProgress(row) {
  current.value = { ...row }
  progress.value = {}
  progressVisible.value = true
  progressLoading.value = true
  try {
    const res = await getEcrProgress(row.id)
    progress.value = res.data || {}
  } catch (e) {
    progress.value = {}
  } finally {
    progressLoading.value = false
  }
}

async function doToEcn(row) {
  await ElMessageBox.confirm(
    `确认将 ${row.ecrNo} 转为 ECN？转单后会生成一张草稿状态的 ECN。`,
    '转 ECN 确认', { type: 'warning' }
  )
  const res = await toEcn(row.id)
  ElMessage.success(`已生成 ${res.data?.ecnNo || 'ECN'}`)
  loadData()
}

async function doClose(row) {
  await ElMessageBox.confirm(`确认关闭 ${row.ecrNo}？关闭后流程终止。`, '关闭确认', { type: 'warning' })
  await closeEcr(row.id)
  ElMessage.success('已关闭')
  loadData()
}

async function doDelete(row) {
  await ElMessageBox.confirm(`确认删除 ${row.ecrNo}？`, '删除确认', { type: 'warning' })
  await deleteEcr(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.ecr-page { padding: 4px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-head h2 { margin: 0 0 4px; font-size: 18px; }
.page-head .sub { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.filter-card { margin-bottom: 12px; }
.filter-card :deep(.el-form-item) { margin-bottom: 0; }
.pager { margin-top: 12px; justify-content: flex-end; }
.sec-title { margin: 18px 0 10px; font-size: 13px; color: var(--el-text-color-regular); }
.node-title { font-size: 13px; font-weight: 600; }
.node-meta { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
</style>
