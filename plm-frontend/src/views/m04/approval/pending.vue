<template>
  <div class="pending-page">
    <div class="page-head">
      <div>
        <h2>我的待办</h2>
        <p class="sub">所有走审批引擎的单据（ECN / ECR / 文档发布）汇总在这里，按等待时长排序，超过 24 小时标红。</p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never" class="plan-approvals">
      <template #header><span>计划 / 完成审批（待处理）</span></template>
      <el-table :data="planRows" v-loading="planLoading" border stripe size="small" empty-text="暂无待审批">
        <el-table-column label="项目" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectNo }} <span style="color:#909399">{{ row.projectName }}</span></template>
        </el-table-column>
        <el-table-column label="节点" width="150">
          <template #default="{ row }">{{ row.nodeName || row.nodeCode }}</template>
        </el-table-column>
        <el-table-column label="计划时间" width="120">
          <template #default="{ row }">{{ row.planDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.approvalType === 'completion' ? 'success' : 'warning'">
              {{ row.approvalType === 'completion' ? '完成审批' : '计划审批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedBy" label="提交人" width="100" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmt(row.completionSubmittedAt || row.planSubmittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="approvePlanRow(row)">通过</el-button>
            <el-button link type="danger" size="small" @click="rejectPlanRow(row)">驳回</el-button>
            <el-button link type="primary" size="small" @click="goProgress(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe size="small" empty-text="没有待办，清爽">
        <el-table-column label="单据类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ OBJECT_TYPE[row.objectType] || row.objectType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="objectNo" label="单号" width="180" />
        <el-table-column prop="flowName" label="流程" min-width="200" show-overflow-tooltip />
        <el-table-column label="当前节点" width="150">
          <template #default="{ row }">
            第 {{ row.nodeSeq }} 节 · {{ row.nodeName }}
          </template>
        </el-table-column>
        <el-table-column label="等待时长" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="waitType(row.waitingHours)">
              {{ row.waitingHours != null ? row.waitingHours + ' h' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到达时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="openAct(row, 'APPROVE')">通过</el-button>
            <el-button link type="danger" size="small" @click="openAct(row, 'REJECT')">驳回</el-button>
            <el-button link size="small" @click="openAct(row, 'BACK')">退回</el-button>
            <el-button link type="primary" size="small" @click="goDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="actVisible" :title="ACT_LABEL[actForm.action] + '确认'" width="480px" destroy-on-close>
      <el-descriptions :column="1" size="small" border style="margin-bottom: 14px">
        <el-descriptions-item label="单号">{{ current.objectNo }}</el-descriptions-item>
        <el-descriptions-item label="节点">第 {{ current.nodeSeq }} 节 · {{ current.nodeName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="70px">
        <el-form-item label="动作">
          <el-radio-group v-model="actForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REJECT">驳回</el-radio-button>
            <el-radio-button value="BACK">退回</el-radio-button>
            <el-radio-button value="TERMINATE">终止</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="actForm.comment" type="textarea" :rows="3" placeholder="通过可留空，其余动作必须填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAct">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pendingTasks, actInstance } from '@/api/approval'
import request from '@/utils/request'
import { approveNodePlan, approveNodeCompletion, rejectNodePlan, rejectNodeCompletion } from '@/api/progress'

const router = useRouter()

const OBJECT_TYPE = { ECN: 'ECN 变更', ECR: 'ECR 申请', DOCUMENT: '文档发布', REQUIREMENT: '需求', RISK: '风险' }
const ACT_LABEL = { APPROVE: '通过', REJECT: '驳回', BACK: '退回', TERMINATE: '终止' }

const rows = ref([])
const loading = ref(false)
const actVisible = ref(false)
const saving = ref(false)
const current = ref({})
const actForm = reactive({ action: 'APPROVE', comment: '' })

function fmt(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

function waitType(h) {
  if (h == null) return 'info'
  if (h >= 48) return 'danger'
  if (h >= 24) return 'warning'
  return 'success'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pendingTasks()
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openAct(row, action) {
  current.value = { ...row }
  actForm.action = action
  actForm.comment = ''
  actVisible.value = true
}

async function submitAct() {
  if (actForm.action !== 'APPROVE' && !actForm.comment.trim()) {
    ElMessage.warning('非通过动作必须填写意见')
    return
  }
  saving.value = true
  try {
    await actInstance(current.value.instanceId, { ...actForm })
    ElMessage.success('已处理')
    actVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

function goDetail(row) {
  if (row.objectType === 'ECR') {
    router.push('/ecr/list')
  } else if (row.objectType === 'ECN') {
    router.push('/ecn/list')
  } else if (row.objectType === 'DOCUMENT') {
    router.push('/document/list')
  } else {
    ElMessage.info('该单据类型暂无详情页')
  }
}

// ===== 计划/完成审批待处理 =====
const planRows = ref([])
const planLoading = ref(false)
async function loadPlanApprovals() {
  planLoading.value = true
  try {
    const r = await request({ url: '/project/approvals/pending', method: 'get' })
    planRows.value = r.data || []
  } finally { planLoading.value = false }
}
async function approvePlanRow(row) {
  try {
    if (row.approvalType === 'completion') await approveNodeCompletion(row.projectId, row.nodeCode)
    else await approveNodePlan(row.projectId, row.nodeCode)
    ElMessage.success('已通过')
    loadPlanApprovals()
  } catch (e) { /* 错误已由 request 弹出 */ }
}
async function rejectPlanRow(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回审批', {
      inputPlaceholder: '如：日期/证据不合理', confirmButtonText: '驳回', cancelButtonText: '取消'
    })
    if (row.approvalType === 'completion') await rejectNodeCompletion(row.projectId, row.nodeCode, value)
    else await rejectNodePlan(row.projectId, row.nodeCode, value)
    ElMessage.success('已驳回')
    loadPlanApprovals()
  } catch (e) { /* 取消或失败 */ }
}
function goProgress(row) {
  router.push({ path: '/project/progress', query: { project: row.projectId, node: row.nodeCode } })
}

onMounted(() => { loadData(); loadPlanApprovals() })
</script>

<style scoped>
.pending-page { padding: 4px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-head h2 { margin: 0 0 4px; font-size: 18px; }
.page-head .sub { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
