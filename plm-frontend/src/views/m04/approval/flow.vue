<template>
  <div class="flow-page">
    <div class="page-head">
      <div>
        <h2>审批流配置</h2>
        <p class="sub">流程定义存库，改节点不用发版。同一单据类型只允许一个「启用」流程，启用时会自动降级其余流程。</p>
      </div>
      <div>
        <el-select v-model="filterType" placeholder="全部单据类型" clearable style="width: 170px; margin-right: 8px" @change="loadFlows">
          <el-option v-for="(v, k) in OBJECT_TYPE" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button :icon="Refresh" @click="loadFlows">刷新</el-button>
        <el-button v-if="canEdit" type="primary" :icon="Plus" @click="openCreate">新建流程</el-button>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :span="7">
        <el-card shadow="never" body-style="padding: 0">
          <el-table
            :data="flows"
            v-loading="loading"
            size="small"
            highlight-current-row
            :current-row-key="current.id"
            row-key="id"
            empty-text="暂无流程"
            @current-change="onSelectFlow">
            <el-table-column prop="name" label="流程" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" width="88" align="center">
              <template #default="{ row }">{{ OBJECT_TYPE[row.objectType] || row.objectType }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="FLOW_STATUS[row.status]?.type || 'info'">
                  {{ FLOW_STATUS[row.status]?.label || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="canEdit && row.status !== 'ENABLED'"
                  link type="success" size="small" @click.stop="toggle(row, 'ENABLED')">启用</el-button>
                <el-button
                  v-if="canEdit && row.status === 'ENABLED'"
                  link type="warning" size="small" @click.stop="toggle(row, 'DISABLED')">停用</el-button>
                <el-button
                  v-if="canEdit"
                  link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="17">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{ form.id ? '编辑流程：' + form.name : '新建流程' }}</span>
              <div>
                <el-button size="small" @click="resetForm">清空</el-button>
                <el-button v-if="canEdit" size="small" type="primary" :loading="saving" @click="save">保存流程</el-button>
              </div>
            </div>
          </template>

          <el-form :model="form" label-width="92px" size="small">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="流程编码" required>
                  <el-input v-model="form.flowCode" placeholder="如 ECR_DEFAULT，唯一" :disabled="!!form.id" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="单据类型" required>
                  <el-select v-model="form.objectType" style="width: 100%" :disabled="!!form.id">
                    <el-option v-for="(v, k) in OBJECT_TYPE" :key="k" :label="v" :value="k" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="流程名称" required>
                  <el-input v-model="form.name" placeholder="如 ECR 变更申请审批" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="版本">
                  <el-input v-model="form.versionNo" placeholder="V1" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="状态">
                  <el-select v-model="form.status" style="width: 100%">
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="启用" value="ENABLED" />
                    <el-option label="停用" value="DISABLED" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" placeholder="选填" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <div class="node-head">
            <span>审批节点（按顺序执行，同一并行组会签）</span>
            <el-button v-if="canEdit" size="small" :icon="Plus" @click="addNode">加节点</el-button>
          </div>

          <el-table :data="form.nodes" size="small" border empty-text="至少配置一个节点">
            <el-table-column label="#" width="46" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="节点名" width="130">
              <template #default="{ row }">
                <el-input v-model="row.nodeName" size="small" placeholder="研发一审" />
              </template>
            </el-table-column>
            <el-table-column label="审批人类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.approverType" size="small">
                  <el-option v-for="(v, k) in APPROVER_TYPE" :key="k" :label="v" :value="k" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="角色 / 账号" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.approverValue" size="small" placeholder="ENGINEER,ADMIN" />
              </template>
            </el-table-column>
            <el-table-column label="条件" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.conditionExpr" size="small" placeholder="change_type='MATERIAL'" />
              </template>
            </el-table-column>
            <el-table-column label="限时h" width="80">
              <template #default="{ row }">
                <el-input-number v-model="row.timeoutHours" size="small" :min="0" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="驳回后" width="110">
              <template #default="{ row }">
                <el-select v-model="row.onReject" size="small">
                  <el-option label="退回上一节" value="BACK" />
                  <el-option label="直接终止" value="TERMINATE" />
                  <el-option label="退回申请人" value="TO_APPLICANT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="超时" width="100">
              <template #default="{ row }">
                <el-select v-model="row.onTimeout" size="small">
                  <el-option label="仅提醒" value="REMIND" />
                  <el-option label="自动通过" value="AUTO_PASS" />
                  <el-option label="升级" value="ESCALATE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column width="56" align="center">
              <template #default="{ $index }">
                <el-button v-if="canEdit" link type="danger" size="small" @click="removeNode($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>

          <p class="hint">
            说明：条件留空表示必经节点；写 <code>change_type='MATERIAL'</code> 时，只有上下文满足才执行该节点。
            DEPT_LEADER / POSITION 暂按角色码解析（部门主管字段尚未建），解析不到人会回落 ADMIN。
          </p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { listFlows, listNodes, saveFlow, changeFlowStatus, deleteFlow } from '@/api/approval'

const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('approval:edit'))

const OBJECT_TYPE = {
  ECN: 'ECN 变更',
  ECR: 'ECR 申请',
  DOCUMENT: '文档发布',
  REQUIREMENT: '需求评审',
  RISK: '技术风险'
}

const FLOW_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  ENABLED: { label: '启用', type: 'success' },
  DISABLED: { label: '停用', type: 'warning' }
}

const APPROVER_TYPE = {
  ROLE: '角色',
  USER: '指定人',
  POSITION: '岗位',
  PROJECT_ROLE: '项目角色',
  DEPT_LEADER: '部门主管'
}

const filterType = ref('')
const flows = ref([])
const loading = ref(false)
const saving = ref(false)
const current = ref({})

const form = reactive({
  id: null, flowCode: '', objectType: 'ECR', name: '', versionNo: 'V1',
  status: 'DRAFT', remark: '', nodes: []
})

function resetForm() {
  Object.assign(form, {
    id: null, flowCode: '', objectType: 'ECR', name: '', versionNo: 'V1',
    status: 'DRAFT', remark: '', nodes: [blankNode()]
  })
  current.value = {}
}

function blankNode() {
  return {
    nodeName: '', nodeCode: '', approverType: 'ROLE', approverValue: '',
    conditionExpr: '', parallelGroup: '', timeoutHours: 24,
    onReject: 'TERMINATE', onTimeout: 'REMIND'
  }
}

function addNode() {
  form.nodes.push(blankNode())
}

function removeNode(i) {
  form.nodes.splice(i, 1)
}

async function loadFlows() {
  loading.value = true
  try {
    const res = await listFlows(filterType.value || undefined)
    flows.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function onSelectFlow(row) {
  if (!row) return
  current.value = { ...row }
  const res = await listNodes(row.id)
  const nodes = (res.data || []).map(n => ({ ...n }))
  Object.assign(form, {
    id: row.id,
    flowCode: row.flowCode,
    objectType: row.objectType,
    name: row.name,
    versionNo: row.versionNo || 'V1',
    status: row.status,
    remark: row.remark,
    nodes: nodes.length ? nodes : [blankNode()]
  })
}

function openCreate() {
  resetForm()
}

async function save() {
  if (!form.flowCode || !form.objectType || !form.name) {
    ElMessage.warning('流程编码、单据类型、流程名称必填')
    return
  }
  if (!form.nodes.length) {
    ElMessage.warning('至少配置一个审批节点')
    return
  }
  for (const [i, n] of form.nodes.entries()) {
    if (!n.nodeName) { ElMessage.warning(`第 ${i + 1} 个节点缺节点名`); return }
    if (!n.approverValue) { ElMessage.warning(`第 ${i + 1} 个节点缺审批人（角色码或账号）`); return }
    n.nodeCode = n.nodeCode || `${form.flowCode}_N${i + 1}`
    n.seq = i + 1
  }
  saving.value = true
  try {
    const res = await saveFlow({ ...form })
    ElMessage.success('已保存')
    await loadFlows()
    if (res.data?.id) onSelectFlow(res.data)
  } finally {
    saving.value = false
  }
}

async function toggle(row, status) {
  await changeFlowStatus(row.id, status)
  ElMessage.success(status === 'ENABLED' ? '已启用（同类型其余流程已自动降级）' : '已停用')
  loadFlows()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除流程「${row.name}」？在途实例不受影响，但后续无法再发起。`, '删除确认', { type: 'warning' })
  await deleteFlow(row.id)
  ElMessage.success('已删除')
  if (current.value.id === row.id) resetForm()
  loadFlows()
}

onMounted(() => {
  resetForm()
  loadFlows()
})
</script>

<style scoped>
.flow-page { padding: 4px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-head h2 { margin: 0 0 4px; font-size: 18px; }
.page-head .sub { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.node-head { display: flex; justify-content: space-between; align-items: center; margin: 4px 0 8px; font-size: 13px; }
.hint { margin: 10px 0 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
.hint code { background: var(--el-fill-color-light); padding: 1px 4px; border-radius: 3px; }
</style>
