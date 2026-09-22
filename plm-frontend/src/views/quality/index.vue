<template>
  <div class="page-container">
    <OperationGuide module-key="quality" />
    <DataIOBar module="quality" name="品质检验标准" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="料号"><el-input v-model="query.partNo" clearable /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable style="width:150px">
            <el-option v-for="(v,k) in categoryMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width:130px">
            <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()" v-if="userStore.hasPermission('quality:add')">新增标准</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="partNo" label="料号" min-width="150" />
        <el-table-column label="分类" width="120"><template #default="{row}">{{ categoryMap[row.category] }}</template></el-table-column>
        <el-table-column prop="itemName" label="检验项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="standardValue" label="标准值" min-width="120" />
        <el-table-column prop="toleranceRange" label="公差范围" min-width="120" />
        <el-table-column prop="tool" label="检验工具" width="120" />
        <el-table-column prop="drawingVersion" label="图纸版本" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusMap[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" v-if="row.status==='DRAFT'" @click="handleRelease(row)">生效</el-button>
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="关联料号" prop="partNo"><el-input v-model="form.partNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="检验分类" prop="category">
            <el-select v-model="form.category" style="width:100%">
              <el-option v-for="(v,k) in categoryMap" :key="k" :label="v" :value="k" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="检验项目" prop="itemName"><el-input v-model="form.itemName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标准值"><el-input v-model="form.standardValue" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="公差范围"><el-input v-model="form.toleranceRange" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="检验工具"><el-input v-model="form.tool" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="参考图纸版本"><el-input v-model="form.drawingVersion" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="检验方法"><el-input v-model="form.method" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="判定标准"><el-input v-model="form.criteria" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="不良定义"><el-input v-model="form.defectDef" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="参考3D位置"><el-input v-model="form.model3dPosition" /></el-form-item></el-col>
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
import { useUserStore } from '@/stores/user'
import { pageQuality, addQuality, updateQuality, deleteQuality, releaseQuality } from '@/api/quality'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()
const categoryMap = { DIMENSION:'尺寸检验', APPEARANCE:'外观检验', ASSEMBLY:'装配检验', FUNCTION:'功能检验', WATERPROOF:'防水结构检验', INCOMING:'来料检验' }
const statusMap = { DRAFT:'草稿', IN_REVIEW:'评审中', RELEASED:'正式发布', CHANGING:'变更中', OBSOLETE:'作废', SEALED:'封存归档' }
const statusTag = (s) => ({ DRAFT:'info', RELEASED:'success', CHANGING:'warning', OBSOLETE:'danger', SEALED:'info' }[s]||'info')
const query = reactive({ pageNum:1, pageSize:10, partNo:'', category:'', status:'' })
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})
const rules = {
  partNo: [{ required:true, message:'请输入关联料号', trigger:'blur' }],
  category: [{ required:true, message:'请选择检验分类', trigger:'change' }],
  itemName: [{ required:true, message:'请输入检验项目', trigger:'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageQuality(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑检验标准' }
  else { form.category = 'DIMENSION'; dialog.title = '新增检验标准' }
  dialog.visible = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) { await updateQuality(form); ElMessage.success('修改成功') }
    else { await addQuality(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleRelease(row) {
  await ElMessageBox.confirm(`生效检验标准[${row.itemName}]? 图纸版本更新时需同步`, '确认', { type:'warning' })
  await releaseQuality(row.id)
  ElMessage.success('已生效')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除检验标准[${row.itemName}]?`, '警告', { type:'warning' })
  await deleteQuality(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
