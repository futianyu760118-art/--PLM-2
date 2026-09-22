<template>
  <div class="page-container">
    <OperationGuide module-key="ecn" />
    <DataIOBar module="ecn" name="ECN工程变更" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="ECN单号"><el-input v-model="query.ecnNo" placeholder="ECN单号" clearable /></el-form-item>
        <el-form-item label="关联料号"><el-input v-model="query.partNo" placeholder="物料料号" clearable /></el-form-item>
        <el-form-item label="变更类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(v, k) in changeTypeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()" v-if="userStore.hasPermission('ecn:add')">新建ECN</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="ecnNo" label="ECN单号" min-width="170" fixed />
        <el-table-column prop="partNo" label="关联料号" min-width="150" />
        <el-table-column prop="materialName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="变更类型" width="110">
          <template #default="{ row }">{{ changeTypeMap[row.changeType] }}</template>
        </el-table-column>
        <el-table-column prop="versionBefore" label="变更前版本" width="100" align="center" />
        <el-table-column prop="versionAfter" label="变更后版本" width="100" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column prop="applyTime" label="申请时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" v-if="(row.status==='DRAFT'||row.status==='REJECTED') && userStore.hasPermission('ecn:add')" @click="openDialog(row)">修改</el-button>
            <el-button link type="success" size="small" v-if="(row.status==='DRAFT'||row.status==='REJECTED') && userStore.hasPermission('ecn:add')" @click="handleSubmit(row)">提交</el-button>
            <el-button link type="warning" size="small" v-if="row.status==='PENDING_L1' && userStore.hasPermission('ecn:review1')" @click="openReview(row,'l1')">一审</el-button>
            <el-button link type="warning" size="small" v-if="row.status==='PENDING_L2' && userStore.hasPermission('ecn:review2')" @click="openReview(row,'l2')">二审</el-button>
            <el-button link type="success" size="small" v-if="row.status==='APPROVED' && userStore.hasPermission('ecn:effect')" @click="handleEffect(row)">生效</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="关联料号" prop="partNo"><el-input v-model="form.partNo" placeholder="物料料号" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="变更类型" prop="changeType">
              <el-select v-model="form.changeType" style="width:100%">
                <el-option v-for="(v,k) in changeTypeMap" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="变更前版本"><el-input v-model="form.versionBefore" placeholder="自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="变更后版本"><el-input v-model="form.versionAfter" placeholder="自动递增" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="改模成本"><el-input-number v-model="form.moldCost" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="变更位置"><el-input v-model="form.changeLocation" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="变更原因" prop="changeReason"><el-input v-model="form.changeReason" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="影响范围"><el-input v-model="form.impactScope" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="试制影响"><el-input v-model="form.trialImpact" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="量产影响"><el-input v-model="form.massImpact" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialog.visible" title="审批" width="480px">
      <el-form label-width="80px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="reviewDialog.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="reviewDialog.comment" type="textarea" :rows="3" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交审批</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detail.visible" title="ECN详情 + 审批流转" size="60%">
      <el-descriptions :column="2" border v-if="detail.data">
        <el-descriptions-item label="ECN单号">{{ detail.data.ecnNo }}</el-descriptions-item>
        <el-descriptions-item label="关联料号">{{ detail.data.partNo }}</el-descriptions-item>
        <el-descriptions-item label="变更类型">{{ changeTypeMap[detail.data.changeType] }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusTag(detail.data.status)">{{ statusMap[detail.data.status] }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="变更前版本">{{ detail.data.versionBefore }}</el-descriptions-item>
        <el-descriptions-item label="变更后版本">{{ detail.data.versionAfter }}</el-descriptions-item>
        <el-descriptions-item label="变更原因" :span="2">{{ detail.data.changeReason }}</el-descriptions-item>
        <el-descriptions-item label="改模成本">{{ detail.data.moldCost }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.data.applicant }}</el-descriptions-item>
        <el-descriptions-item label="一审人">{{ detail.data.reviewL1By }}</el-descriptions-item>
        <el-descriptions-item label="二审人">{{ detail.data.reviewL2By }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">审批流转记录</el-divider>
      <el-timeline>
        <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt" placement="top"
          :type="log.action==='reject'?'danger':(log.action==='effect'?'success':'primary')">
          <h4>{{ actionLabel(log) }}</h4>
          <p>{{ log.comment }}</p>
          <p style="color:#909399;font-size:12px">{{ log.operator }} ({{ log.operatorRole }}) · {{ log.fromStatus }} → {{ log.toStatus }}</p>
        </el-timeline-item>
      </el-timeline>

      <el-divider content-position="left">变更影响面</el-divider>
      <div style="margin-bottom:10px">
        <el-button size="small" type="primary" @click="saveImpacts">生成影响清单</el-button>
        <span style="margin-left:8px;font-size:12px;color:#999">影响类型: 物料/文件/BOM/模具/工艺/贸易</span>
      </div>
      <el-table :data="detail.impacts" border size="small">
        <el-table-column prop="impactType" label="影响类型" width="100">
          <template #default="{row}">{{ impactTypeMap[row.impactType] || row.impactType }}</template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标对象" width="100" />
        <el-table-column prop="actionCode" label="动作" width="120" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.status==='DONE'?'success':row.status==='PENDING'?'warning':'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handledAt" label="处理时间" width="160" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pageEcn, addEcn, updateEcn, submitEcn, reviewL1Approve, reviewL1Reject, reviewL2Approve, reviewL2Reject, effectEcn, getEcn, getEcnLogs, getEcnImpacts, saveEcnImpacts } from '@/api/ecn'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()

const changeTypeMap = { STRUCTURE: '结构变更', MOLD: '模具变更', PROCESS: '工艺变更', BOM: 'BOM变更', DIMENSION: '尺寸变更' }
const statusMap = { DRAFT: '草稿', PENDING_L1: '待一审', PENDING_L2: '待二审', APPROVED: '审批通过', REJECTED: '审批驳回', EFFECTIVE: '已生效', VOID: '已作废' }
const statusTag = (s) => ({ DRAFT: 'info', PENDING_L1: 'warning', PENDING_L2: 'warning', APPROVED: 'primary', REJECTED: 'danger', EFFECTIVE: 'success', VOID: 'info' }[s] || 'info')
const impactTypeMap = { PART: '物料版本', FILE: '图纸文件', BOM: 'BOM结构', MOLD: '模具', SOP: '工艺SOP', TRADE: '贸易文档' }

const query = reactive({ pageNum: 1, pageSize: 10, ecnNo: '', partNo: '', changeType: '', status: '' })
const dialog = reactive({ visible: false, title: '' })
const reviewDialog = reactive({ visible: false, id: null, level: '', approve: true, comment: '' })
const detail = reactive({ visible: false, data: null, logs: [], impacts: [] })
const form = reactive({})
const rules = {
  partNo: [{ required: true, message: '请输入关联料号', trigger: 'blur' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  changeReason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageEcn(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, pageSize: 10, ecnNo: '', partNo: '', changeType: '', status: '' })
  loadData()
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '修改ECN' }
  else { form.changeType = 'STRUCTURE'; form.moldCost = 0; dialog.title = '新建ECN' }
  dialog.visible = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) { await updateEcn(form); ElMessage.success('修改成功') }
    else { await addEcn(form); ElMessage.success('新建成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleSubmit(row) {
  await ElMessageBox.confirm(`提交ECN [${row.ecnNo}] 进入审批流?`, '确认', { type: 'info' })
  await submitEcn(row.id)
  ElMessage.success('已提交审批')
  loadData()
}

function openReview(row, level) {
  reviewDialog.id = row.id
  reviewDialog.level = level
  reviewDialog.approve = true
  reviewDialog.comment = ''
  reviewDialog.visible = true
}

async function submitReview() {
  if (!reviewDialog.comment) { ElMessage.warning('请输入审批意见'); return }
  submitting.value = true
  try {
    const { id, level, approve, comment } = reviewDialog
    if (level === 'l1') { approve ? await reviewL1Approve(id, { comment }) : await reviewL1Reject(id, { comment }) }
    else { approve ? await reviewL2Approve(id, { comment }) : await reviewL2Reject(id, { comment }) }
    ElMessage.success(approve ? '审批通过' : '已驳回')
    reviewDialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleEffect(row) {
  await ElMessageBox.confirm(`ECN [${row.ecnNo}] 生效后, 物料版本将自动升级至 ${row.versionAfter}, 旧版图纸自动作废封存。确认生效?`, 'ECN生效', { type: 'warning' })
  await effectEcn(row.id)
  ElMessage.success('ECN已生效,版本已升级')
  loadData()
}

async function viewDetail(row) {
  const [d, logs, impacts] = await Promise.all([getEcn(row.id), getEcnLogs(row.id), getEcnImpacts(row.id)])
  detail.data = d.data
  detail.logs = logs.data
  detail.impacts = impacts.data || []
  detail.visible = true
}

async function saveImpacts() {
  if (!detail.data) return
  await saveEcnImpacts(detail.data.id, detail.data.ecnNo, ['PART', 'FILE', 'BOM', 'MOLD', 'SOP', 'TRADE'])
  ElMessage.success('影响清单已生成')
  const res = await getEcnImpacts(detail.data.id)
  detail.impacts = res.data || []
}

function actionLabel(log) {
  return { submit: '提交审批', approve: '审批通过', reject: '审批驳回', effect: 'ECN生效(版本升级)', void: '作废' }[log.action] || log.action
}

onMounted(loadData)
</script>
