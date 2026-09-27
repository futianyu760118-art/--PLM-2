<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable /></el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="问题/产品/提出人" /></el-form-item>
        <el-form-item label="闭环">
          <el-select v-model="query.closed" clearable style="width:120px">
            <el-option label="已闭环" :value="1" /><el-option label="未闭环" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增异常单</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="occurDate" label="发生日期" width="110" />
        <el-table-column prop="proposer" label="提出人" width="90" />
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="单号" width="120" />
        <el-table-column prop="projectNo" label="项目号" width="140" />
        <el-table-column prop="problemDesc" label="问题描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="responsiblePerson" label="责任人" width="90" />
        <el-table-column prop="responsibleDept" label="责任部门" width="100" />
        <el-table-column prop="planCompleteDate" label="计划完成" width="110" />
        <el-table-column label="闭环" width="80" align="center">
          <template #default="{row}"><el-tag :type="row.closed ? 'success' : 'warning'" size="small">{{ row.closed ? '已闭环' : '未闭环' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="发生日期"><el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="提出人"><el-input v-model="form.proposer" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品名称"><el-input v-model="form.productName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单号"><el-input v-model="form.orderNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目号"><el-input v-model="form.projectNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="责任人"><el-input v-model="form.responsiblePerson" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="责任部门"><el-input v-model="form.responsibleDept" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划完成"><el-date-picker v-model="form.planCompleteDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="长期措施完成"><el-date-picker v-model="form.longTermDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="稽核"><el-input v-model="form.audit" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="问题描述"><el-input v-model="form.problemDesc" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="临时措施"><el-input v-model="form.tempMeasure" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="原因分析"><el-input v-model="form.causeAnalysis" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="长期措施"><el-input v-model="form.longTermMeasure" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="闭环">
            <el-switch v-model="form.closed" :active-value="1" :inactive-value="0" />
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remarks" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageSupplyIssues, createSupplyIssue, updateSupplyIssue, deleteSupplyIssue } from '@/api/project'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()
const query = reactive({ pageNum: 1, pageSize: 10, projectNo: '', keyword: '', closed: null })
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageSupplyIssues(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑供应链品质异常' }
  else { form.closed = 0; dialog.title = '新增供应链品质异常' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateSupplyIssue(form); ElMessage.success('修改成功') }
    else { await createSupplyIssue(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该异常单?', '警告', { type: 'warning' })
  await deleteSupplyIssue(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
