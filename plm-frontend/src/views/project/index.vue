<template>
  <div class="page-container">
    <OperationGuide module-key="project" />
    <DataIOBar module="project" name="研发项目NPI" />
    <el-card>
      <el-form :inline="true" :model="query">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="编号/名称/料号/客户" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="s in statusMap" :key="s.v" :label="s.l" :value="s.v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新建项目</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="projectNo" label="项目编号" width="160" fixed />
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="partNo" label="关联料号" width="140" />
        <el-table-column prop="customerName" label="客户" width="120" show-overflow-tooltip />
        <el-table-column prop="owner" label="负责人" width="80" />
        <el-table-column label="当前门" width="90" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="gateTag(row.gateStatus)">{{ row.currentGate }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetDate" label="目标日期" width="110" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{row}">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openGates(row)">阶段门</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="640px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="项目名称" required><el-input v-model="form.projectName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关联料号"><el-input v-model="form.partNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标日期"><el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="紧急度">
            <el-select v-model="form.urgency" style="width:100%">
              <el-option label="正常" value="normal" /><el-option label="紧急" value="urgent" /><el-option label="特急" value="critical" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="订单额"><el-input v-model="form.orderAmount" type="number" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="投入额"><el-input v-model="form.investAmount" type="number" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remarks" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="gateDrawer.visible" :title="'阶段门 - ' + (gateDrawer.project?.projectName || '')" size="650px">
      <div style="margin-bottom:14px">
        <el-steps :active="gateDrawer.currentStep" align-center finish-status="success">
          <el-step v-for="g in gateList" :key="g.code" :title="g.code" :description="g.name" />
        </el-steps>
      </div>
      <div style="margin-bottom:10px;text-align:center">
        <el-button type="success" @click="handlePassNext" :disabled="!gateDrawer.nextGate">通过 {{ gateDrawer.nextGate || '—' }}</el-button>
      </div>
      <el-divider>流转记录</el-divider>
      <el-timeline>
        <el-timeline-item v-for="log in gateDrawer.logs" :key="log.id" :timestamp="log.createdAt" placement="top"
          :type="log.result==='PASS'?'success':'danger'">
          <b>{{ log.gateCode }} {{ log.gateName }}</b> — {{ log.result }}
          <p v-if="log.comment">{{ log.comment }}</p>
          <p style="color:#999;font-size:12px">{{ log.operator }} · {{ log.actualDate }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageProject, createProject, updateProject, deleteProject, getGateLogs, passGate, getGateDefs } from '@/api/project'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const statusMap = [
  { v: 'ACTIVE', l: '进行中' }, { v: 'MP', l: '已量产' }, { v: 'CLOSED', l: '已关闭' }, { v: 'ON_HOLD', l: '暂停' }
]
const statusLabel = (s) => ({ ACTIVE:'进行中', MP:'已量产', CLOSED:'已关闭', ON_HOLD:'暂停' }[s] || s)
const gateTag = (gs) => ({ ON_TRACK:'success', BLOCKED:'danger', DELAYED:'warning' }[gs] || 'info')
const gateList = [
  { code:'G0', name:'概念' },{ code:'G1', name:'方案' },{ code:'G2', name:'结构冻结' },
  { code:'G3', name:'开模' },{ code:'G4', name:'T0/T1' },{ code:'G5', name:'T2/工试' },
  { code:'G6', name:'技转' },{ code:'G7', name:'量产' },{ code:'G8', name:'关闭' }
]

const query = reactive({ pageNum:1, pageSize:10, keyword:'', status:'' })
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})
const gateDrawer = reactive({ visible:false, project:null, logs:[], currentStep:0, nextGate:'' })

async function loadData() {
  loading.value = true
  try {
    const res = await pageProject(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetQuery() { Object.assign(query, { pageNum:1, keyword:'', status:'' }); loadData() }

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑项目' }
  else { form.urgency = 'normal'; form.projectType = 'NEW'; dialog.title = '新建项目' }
  dialog.visible = true
}

async function submitForm() {
  if (!form.projectName) { ElMessage.warning('请输入项目名称'); return }
  submitting.value = true
  try {
    if (form.id) { await updateProject(form); ElMessage.success('修改成功') }
    else { await createProject(form); ElMessage.success('创建成功') }
    dialog.visible = false; loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除项目 [${row.projectNo}]?`, '警告', { type:'warning' })
  await deleteProject(row.id); ElMessage.success('已删除'); loadData()
}

async function openGates(row) {
  gateDrawer.project = row
  gateDrawer.visible = true
  const res = await getGateLogs(row.id)
  gateDrawer.logs = res.data || []
  const idx = gateList.findIndex(g => g.code === row.currentGate)
  gateDrawer.currentStep = idx >= 0 ? idx : 0
  gateDrawer.nextGate = idx >= 0 && idx < gateList.length - 1 ? gateList[idx + 1].code : ''
}

async function handlePassNext() {
  if (!gateDrawer.nextGate || !gateDrawer.project) return
  await ElMessageBox.confirm(`确认通过阶段门 ${gateDrawer.nextGate}?`, '确认', { type:'success' })
  await passGate(gateDrawer.project.id, gateDrawer.nextGate, {})
  ElMessage.success(`${gateDrawer.nextGate} 已通过`)
  const res = await getGateLogs(gateDrawer.project.id)
  gateDrawer.logs = res.data || []
  const proj = tableData.value.find(p => p.id === gateDrawer.project.id)
  if (proj) { proj.currentGate = gateDrawer.nextGate; proj.gateStatus = 'ON_TRACK' }
  const idx = gateList.findIndex(g => g.code === gateDrawer.nextGate)
  gateDrawer.currentStep = idx
  gateDrawer.nextGate = idx < gateList.length - 1 ? gateList[idx + 1].code : ''
}

onMounted(loadData)
</script>
