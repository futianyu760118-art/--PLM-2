<template>
  <div class="page-container">
    <OperationGuide module-key="outsourcing" />
    <DataIOBar module="outsourcing" name="外协发图" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="外协单位"><el-input v-model="query.outsourceCompany" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width:140px">
            <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()" v-if="userStore.hasPermission('outsourcing:add')">新建发图申请</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="requestNo" label="申请单号" min-width="170" fixed />
        <el-table-column prop="outsourceCompany" label="外协单位" min-width="160" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="drawingType" label="图纸类型" width="120" />
        <el-table-column prop="validityDays" label="有效期(天)" width="100" align="center" />
        <el-table-column prop="expireAt" label="到期时间" width="160" />
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusMap[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="viewFiles(row)">文件包</el-button>
            <el-button link type="success" size="small" v-if="(row.status==='DRAFT'||row.status==='REJECTED') && userStore.hasPermission('outsourcing:add')" @click="handleSubmit(row)">提交</el-button>
            <el-button link type="warning" size="small" v-if="row.status==='PENDING' && userStore.hasPermission('outsourcing:approve')" @click="openApprove(row)">审批</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" title="新建外协发图申请" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="外协单位" prop="outsourceCompany"><el-input v-model="form.outsourceCompany" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" /></el-form-item>
        <el-form-item label="图纸类型"><el-input v-model="form.drawingType" placeholder="如: 模具加工图" /></el-form-item>
        <el-form-item label="有效期" prop="validityDays">
          <el-radio-group v-model="form.validityDays">
            <el-radio :value="7">7天</el-radio>
            <el-radio :value="15">15天</el-radio>
            <el-radio :value="30">30天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="关联文件">
          <el-upload :action="'/api/file/upload'" :headers="uploadHeaders" :on-success="onUploadSuccess" :show-file-list="true" :file-list="fileList">
            <el-button icon="Upload">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="approveDialog.visible" title="外协发图审批" width="460px">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
        title="审批通过后自动生成不可逆水印 + 有效期, 过期文件彻底失效, 所有下载记录留存" />
      <el-form label-width="80px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="approveDialog.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见"><el-input v-model="approveDialog.comment" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="filesDrawer.visible" title="外协文件包" size="50%">
      <el-table :data="filesDrawer.files" border>
        <el-table-column prop="fileId" label="文件ID" width="100" />
        <el-table-column prop="downloadCount" label="下载次数" width="100" align="center" />
        <el-table-column prop="lastDownloadAt" label="最后下载" min-width="160" />
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button link type="primary" size="small" v-if="userStore.hasPermission('outsourcing:download')"
              @click="handleDownload(row)">下载(留痕)</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pageOutsource, createOutsource, submitOutsource, approveOutsource, rejectOutsource, getOutsourceFiles, downloadOutsource } from '@/api/outsourcing'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()
const fileList = ref([])
const statusMap = { DRAFT:'草稿', PENDING:'待审批', APPROVED:'审批通过', REJECTED:'审批驳回', EXPIRED:'已过期', VOID:'已作废' }
const statusTag = (s) => ({ DRAFT:'info', PENDING:'warning', APPROVED:'success', REJECTED:'danger', EXPIRED:'info', VOID:'info' }[s]||'info')
const query = reactive({ pageNum:1, pageSize:10, outsourceCompany:'', status:'' })
const dialog = reactive({ visible:false })
const approveDialog = reactive({ visible:false, id:null, approve:true, comment:'' })
const filesDrawer = reactive({ visible:false, request:null, files:[] })
const form = reactive({ outsourceCompany:'', contactPerson:'', purpose:'', drawingType:'', validityDays:7, description:'', fileIds:[] })
const rules = {
  outsourceCompany: [{ required:true, message:'请输入外协单位', trigger:'blur' }],
  validityDays: [{ required:true, message:'请选择有效期', trigger:'change' }]
}
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('plm_token') }))

async function loadData() {
  loading.value = true
  try {
    const res = await pageOutsource(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog() {
  Object.assign(form, { outsourceCompany:'', contactPerson:'', purpose:'', drawingType:'', validityDays:7, description:'', fileIds:[] })
  fileList.value = []
  dialog.visible = true
}

function onUploadSuccess(res) {
  if (res.code === 200) {
    form.fileIds.push(res.data.id)
    ElMessage.success('文件上传成功')
  }
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await createOutsource(form)
    ElMessage.success('申请创建成功')
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleSubmit(row) {
  await ElMessageBox.confirm(`提交发图申请[${row.requestNo}]审批?`, '确认', { type:'info' })
  await submitOutsource(row.id)
  ElMessage.success('已提交')
  loadData()
}

function openApprove(row) {
  approveDialog.id = row.id
  approveDialog.approve = true
  approveDialog.comment = ''
  approveDialog.visible = true
}

async function submitApprove() {
  submitting.value = true
  try {
    if (approveDialog.approve) {
      await approveOutsource(approveDialog.id, approveDialog.comment)
      ElMessage.success('审批通过,水印+有效期已生成')
    } else {
      await rejectOutsource(approveDialog.id, approveDialog.comment)
      ElMessage.success('已驳回')
    }
    approveDialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function viewFiles(row) {
  filesDrawer.request = row
  const res = await getOutsourceFiles(row.id)
  filesDrawer.files = res.data
  filesDrawer.visible = true
}

async function handleDownload(fileRow) {
  await downloadOutsource(filesDrawer.request.id, fileRow.fileId)
  ElMessage.success('下载已记录')
  const res = await getOutsourceFiles(filesDrawer.request.id)
  filesDrawer.files = res.data
}

onMounted(loadData)
</script>
