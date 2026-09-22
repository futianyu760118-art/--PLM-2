<template>
  <div class="page-container">
    <OperationGuide module-key="mold" />
    <DataIOBar module="mold" name="模具资产" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="模具编号"><el-input v-model="query.moldNo" clearable /></el-form-item>
        <el-form-item label="产品料号"><el-input v-model="query.partNo" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width:130px">
            <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()" v-if="userStore.hasPermission('mold:add')">新增台账</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="moldNo" label="模具编号" min-width="160" fixed />
        <el-table-column prop="partNo" label="产品料号" min-width="140" />
        <el-table-column prop="moldName" label="模具名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="openDate" label="开模日期" width="120" />
        <el-table-column prop="cavityCount" label="模腔数" width="90" align="center" />
        <el-table-column prop="accumulateShots" label="累计啤数" width="110" align="center">
          <template #default="{row}"><span :style="{color: row.accumulateShots > row.maintenanceCycle ? '#f56c6c' : ''}">{{ row.accumulateShots }}</span></template>
        </el-table-column>
        <el-table-column prop="maintenanceCycle" label="保养周期" width="100" align="center" />
        <el-table-column prop="assetValue" label="资产价值" width="110" align="right" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusMap[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openShots(row)">更新啤数</el-button>
            <el-button link type="warning" size="small" @click="openTrials(row)">试模履历</el-button>
            <el-button link type="danger" size="small" v-if="row.status!=='OBSOLETE'" @click="handleScrap(row)">报废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="产品料号"><el-input v-model="form.partNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="模具名称"><el-input v-model="form.moldName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开模日期"><el-date-picker v-model="form.openDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="模腔数量"><el-input-number v-model="form.cavityCount" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保养周期"><el-input-number v-model="form.maintenanceCycle" :min="1000" :step="1000" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="资产价值"><el-input-number v-model="form.assetValue" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态">
            <el-select v-model="form.status" style="width:100%">
              <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shotsDialog.visible" title="更新生产啤数" width="360px">
      <el-form label-width="100px">
        <el-form-item label="模具编号">{{ shotsDialog.moldNo }}</el-form-item>
        <el-form-item label="累计啤数"><el-input-number v-model="shotsDialog.shots" :min="0" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shotsDialog.visible=false">取消</el-button>
        <el-button type="primary" @click="submitShots">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="trialsDrawer.visible" title="试模/修模履历" size="60%">
      <div style="margin-bottom:12px"><el-button type="primary" icon="Plus" @click="openAddTrial">新增记录</el-button></div>
      <el-timeline>
        <el-timeline-item v-for="log in trialsDrawer.logs" :key="log.id" :timestamp="log.trialTime" placement="top" type="warning">
          <h4>第 {{ log.trialCount }} 次试模</h4>
          <p><strong>不良现象:</strong> {{ log.defectPhenomenon }}</p>
          <p><strong>修模位置:</strong> {{ log.repairPosition }}</p>
          <p><strong>整改方案:</strong> {{ log.solution }}</p>
          <p style="color:#909399;font-size:12px">处理人: {{ log.handler }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!trialsDrawer.logs.length" description="暂无试模记录" />
    </el-drawer>

    <el-dialog v-model="trialDialog.visible" title="新增试模/修模记录" width="560px">
      <el-form :model="trialForm" label-width="100px">
        <el-form-item label="试模时间"><el-date-picker v-model="trialForm.trialTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="第几次"><el-input-number v-model="trialForm.trialCount" :min="1" /></el-form-item>
        <el-form-item label="不良现象"><el-input v-model="trialForm.defectPhenomenon" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="修模位置"><el-input v-model="trialForm.repairPosition" /></el-form-item>
        <el-form-item label="尺寸修改"><el-input v-model="trialForm.modifyData" /></el-form-item>
        <el-form-item label="整改方案"><el-input v-model="trialForm.solution" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="trialDialog.visible=false">取消</el-button>
        <el-button type="primary" @click="submitTrial">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pageMold, createMold, updateMold, updateShots, scrapMold, getTrials, addTrial } from '@/api/mold'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const statusMap = { IN_DESIGN:'设计中', IN_MACHINING:'加工中', TRIAL:'试模中', IN_PRODUCTION:'量产中', MAINTENANCE:'维修保养', OBSOLETE:'报废', SEALED:'封存' }
const statusTag = (s) => ({ IN_DESIGN:'info', IN_MACHINING:'info', TRIAL:'warning', IN_PRODUCTION:'success', MAINTENANCE:'warning', OBSOLETE:'danger', SEALED:'info' }[s]||'info')
const query = reactive({ pageNum:1, pageSize:10, moldNo:'', partNo:'', status:'' })
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})
const shotsDialog = reactive({ visible:false, id:null, moldNo:'', shots:0 })
const trialsDrawer = reactive({ visible:false, moldNo:'', logs:[] })
const trialDialog = reactive({ visible:false })
const trialForm = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageMold(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑模具' }
  else { form.cavityCount = 1; form.maintenanceCycle = 10000; form.status = 'IN_DESIGN'; form.accumulateShots = 0; dialog.title = '新增模具' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateMold(form); ElMessage.success('修改成功') }
    else { await createMold(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

function openShots(row) {
  shotsDialog.id = row.id
  shotsDialog.moldNo = row.moldNo
  shotsDialog.shots = row.accumulateShots
  shotsDialog.visible = true
}

async function submitShots() {
  await updateShots(shotsDialog.id, shotsDialog.shots)
  ElMessage.success('啤数已更新')
  shotsDialog.visible = false
  loadData()
}

async function openTrials(row) {
  trialsDrawer.moldNo = row.moldNo
  const res = await getTrials(row.moldNo)
  trialsDrawer.logs = res.data
  trialsDrawer.visible = true
}

function openAddTrial() {
  Object.keys(trialForm).forEach(k => delete trialForm[k])
  trialForm.moldNo = trialsDrawer.moldNo
  trialForm.trialCount = 1
  trialForm.trialTime = new Date().toISOString().slice(0,19)
  trialDialog.visible = true
}

async function submitTrial() {
  await addTrial(trialForm)
  ElMessage.success('记录已添加')
  trialDialog.visible = false
  const res = await getTrials(trialsDrawer.moldNo)
  trialsDrawer.logs = res.data
}

async function handleScrap(row) {
  await ElMessageBox.confirm(`确认模具[${row.moldNo}]报废? 报废后档案持续封存, 禁止删除`, '警告', { type:'warning' })
  await scrapMold(row.id)
  ElMessage.success('已报废')
  loadData()
}

onMounted(loadData)
</script>
