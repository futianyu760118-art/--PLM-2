<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable /></el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="型号/客户/业务员" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增推广记录</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="productModel" label="产品型号" min-width="130" />
        <el-table-column prop="projectNo" label="关联项目" width="140" />
        <el-table-column prop="salesperson" label="业务员" width="100" />
        <el-table-column prop="customer" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="appearance" label="外观" min-width="100" show-overflow-tooltip />
        <el-table-column prop="price" label="价格" min-width="100" show-overflow-tooltip />
        <el-table-column prop="performance" label="性能" min-width="100" show-overflow-tooltip />
        <el-table-column prop="functionFeedback" label="功能" min-width="100" show-overflow-tooltip />
        <el-table-column prop="progress" label="目前进度" min-width="120" show-overflow-tooltip />
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="640px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="产品型号"><el-input v-model="form.productModel" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关联项目号"><el-input v-model="form.projectNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务员"><el-input v-model="form.salesperson" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户"><el-input v-model="form.customer" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="外观反馈"><el-input v-model="form.appearance" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="价格反馈"><el-input v-model="form.price" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性能反馈"><el-input v-model="form.performance" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="功能反馈"><el-input v-model="form.functionFeedback" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="目前进度"><el-input v-model="form.progress" /></el-form-item></el-col>
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
import { pageSalesPromotion, createSalesPromotion, updateSalesPromotion, deleteSalesPromotion } from '@/api/project'

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
    const res = await pageSalesPromotion(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑销售推广进度' }
  else { dialog.title = '新增销售推广进度' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateSalesPromotion(form); ElMessage.success('修改成功') }
    else { await createSalesPromotion(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该推广记录?', '警告', { type: 'warning' })
  await deleteSalesPromotion(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
