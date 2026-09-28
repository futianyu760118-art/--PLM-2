<template>
  <div class="page-container">
    <el-card>
      <el-tabs v-model="activeType" @tab-change="onTabChange">
        <el-tab-pane v-for="d in defs" :key="d.type" :label="d.label" :name="d.type" />
      </el-tabs>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable @keyup.enter="loadData" /></el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button type="primary" plain icon="Plus" @click="openDialog()">新增</el-button>
          <el-button icon="Download" @click="doExport">导出</el-button>
          <el-upload style="display:inline-block;margin-left:10px" :show-file-list="false" :http-request="doImport" accept=".xlsx,.xls">
            <el-button icon="Upload">导入</el-button>
          </el-upload>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe size="small" height="calc(100vh - 320px)">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column v-for="c in curCols" :key="c.key" :prop="c.key" :label="c.label" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12" v-for="c in curCols" :key="c.key">
            <el-form-item :label="c.label">
              <el-input v-if="c.type==='text'" v-model="form[c.key]" />
              <el-input v-else-if="c.type==='number'" v-model="form[c.key]" type="number" />
              <el-select v-else-if="c.type==='select'" v-model="form[c.key]" style="width:100%" clearable>
                <el-option v-for="o in (c.options||'').split(',')" :key="o" :label="o" :value="o" />
              </el-select>
              <el-input v-else v-model="form[c.key]" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBaseMeta, pageBase, createBase, updateBase, deleteBase, exportBase, importBase } from '@/api/base'

const defs = ref([])
const activeType = ref('')
const activeDef = computed(() => defs.value.find(d => d.type === activeType.value) || { cols: [] })
const curCols = computed(() => activeDef.value.cols || [])
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})

function onTabChange() { query.pageNum = 1; loadData() }

async function loadData() {
  if (!activeType.value) return
  loading.value = true
  try {
    const res = await pageBase(activeType.value, query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑 - ' + activeDef.value.label }
  else { dialog.title = '新增 - ' + activeDef.value.label }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateBase(activeType.value, form); ElMessage.success('修改成功') }
    else { await createBase(activeType.value, form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该记录?', '警告', { type: 'warning' })
  await deleteBase(activeType.value, row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function doExport() { await exportBase(activeType.value); }

async function doImport(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  try {
    const res = await importBase(activeType.value, fd)
    ElMessage.success('导入 ' + (res.data?.imported || 0) + ' 条')
    loadData()
  } catch (e) { ElMessage.error('导入失败') }
}

onMounted(async () => {
  const res = await getBaseMeta()
  defs.value = res.data || []
  if (defs.value.length) { activeType.value = defs.value[0].type; loadData() }
})
</script>
