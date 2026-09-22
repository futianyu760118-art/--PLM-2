<template>
  <div class="page-container">
    <OperationGuide module-key="sys-dict" />
    <el-card>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="字典类型">
          <el-select v-model="query.dictType" clearable filterable placeholder="全部" style="width:180px">
            <el-option v-for="t in dictTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增字典</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="dictType" label="字典类型" min-width="160" />
        <el-table-column prop="dictLabel" label="标签" min-width="140" />
        <el-table-column prop="dictValue" label="值" min-width="140" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="字典类型"><el-input v-model="form.dictType" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.dictLabel" /></el-form-item>
        <el-form-item label="值"><el-input v-model="form.dictValue" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">禁用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
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
import { pageDict, createDict, updateDict, deleteDict } from '@/api/system'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const dictTypes = ref(['material_type','material_status','ecn_change_type','ecn_status','inspection_category','outsource_status','mold_status','model3d_type','archive_status'])
const query = reactive({ pageNum:1, pageSize:20, dictType:'' })
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageDict(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑字典' }
  else { form.status = 1; form.sortOrder = 0; dialog.title = '新增字典' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateDict(form); ElMessage.success('修改成功') }
    else { await createDict(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该字典项?', '警告', { type:'warning' })
  await deleteDict(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
