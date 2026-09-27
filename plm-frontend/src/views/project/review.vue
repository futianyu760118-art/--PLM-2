<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable /></el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="项目/规律" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增复盘</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="projectNo" label="项目编号" width="140" />
        <el-table-column prop="projectName" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="goalOriginal" label="回顾目标" min-width="150" show-overflow-tooltip />
        <el-table-column prop="resultActual" label="评估结果" min-width="150" show-overflow-tooltip />
        <el-table-column prop="successFactors" label="成功因素" min-width="140" show-overflow-tooltip />
        <el-table-column prop="failureCauses" label="失败原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="insights" label="经验规律" min-width="140" show-overflow-tooltip />
        <el-table-column prop="actionPlan" label="行动计划" min-width="140" show-overflow-tooltip />
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
          <el-col :span="12"><el-form-item label="项目编号"><el-input v-model="form.projectNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称"><el-input v-model="form.projectName" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="回顾目标"><el-input v-model="form.goalOriginal" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="里程碑目标"><el-input v-model="form.goalMilestone" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="结果亮点"><el-input v-model="form.resultHighlights" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="结果不足"><el-input v-model="form.resultLowlights" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="实际结果"><el-input v-model="form.resultActual" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="成功要素"><el-input v-model="form.successFactors" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="失败原因"><el-input v-model="form.failureCauses" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="洞察规律"><el-input v-model="form.insights" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="经验"><el-input v-model="form.experience" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="行动计划"><el-input v-model="form.actionPlan" type="textarea" :rows="2" /></el-form-item></el-col>
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
import { pageReviews, createReview, updateReview, deleteReview } from '@/api/project'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()
const query = reactive({ pageNum: 1, pageSize: 10, projectNo: '', keyword: '' })
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageReviews(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑项目复盘' }
  else { dialog.title = '新增项目复盘' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateReview(form); ElMessage.success('修改成功') }
    else { await createReview(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该复盘记录?', '警告', { type: 'warning' })
  await deleteReview(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
