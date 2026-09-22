<template>
  <div class="page-container">
    <OperationGuide module-key="model3d" />
    <DataIOBar module="model3d" name="3D模型库" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="料号"><el-input v-model="query.partNo" clearable /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.modelType" clearable style="width:150px">
            <el-option v-for="(v,k) in typeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-upload v-if="userStore.hasPermission('model3d:upload')" :show-file-list="false" :before-upload="beforeUpload">
          <el-button type="primary" icon="Upload">上传3D模型</el-button>
        </el-upload>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="modelName" label="模型名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="partNo" label="料号" min-width="140" />
        <el-table-column label="类型" width="130"><template #default="{row}">{{ typeMap[row.modelType] }}</template></el-table-column>
        <el-table-column prop="sourceFormat" label="格式" width="90" align="center" />
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column prop="ecnNo" label="关联ECN" min-width="150" />
        <el-table-column label="来源" width="90" align="center">
          <template #default="{row}"><el-tag size="small" :type="row.source===1?'success':'info'">{{ row.source===1?'AI生成':'上传' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdBy" label="上传人" width="100" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="handlePreview(row)" v-if="userStore.hasPermission('model3d:preview')">预览</el-button>
            <el-button link type="success" size="small" :loading="convertingId===row.id" @click="handleConvert(row)" v-if="userStore.hasPermission('model3d:upload') && !row.extranetFileId">转换为GLB</el-button>
            <el-button link type="warning" size="small" @click="handleExplode(row)" v-if="userStore.hasPermission('model3d:explode')">3D爆炸</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="uploadDialog.visible" title="上传3D模型" width="480px">
      <el-form label-width="100px">
        <el-form-item label="关联料号" required>
          <el-input v-model="uploadDialog.partNo" placeholder="物料料号" />
        </el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="uploadDialog.modelType" style="width:100%">
            <el-option v-for="(v,k) in typeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-tag>{{ uploadDialog.file?.name }}</el-tag>
        </el-form-item>
        <el-alert type="info" :closable="false" style="margin-bottom:12px"
          title="GLB/glTF 格式可直接在线预览；STEP/IGS/OBJ/STL 需下载后用 CAD 软件查看" />
      </el-form>
      <template #footer>
        <el-button @click="uploadDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog.visible" title="3D在线预览(Three.js)" width="90%" top="3vh" @opened="onPreviewOpen" @closed="onPreviewClose">
      <div style="height:72vh">
        <Model3DViewer v-if="previewDialog.showViewer" :modelUrl="previewDialog.modelUrl" :modelFormat="previewDialog.modelFormat" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import Model3DViewer from '@/components/three/Model3DViewer.vue'
import { pageModel3d, uploadModel3d, explodeModel3d, previewModel3d, convertToGlb } from '@/api/model3d'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const convertingId = ref(null)
const tableData = ref([])
const total = ref(0)
const typeMap = { APPEARANCE:'外观3D', STRUCTURE:'产品结构3D', MOLD_FLOW:'模流报告', MOLD:'模具拆模3D', EXPLODE:'爆炸图' }
const query = reactive({ pageNum:1, pageSize:10, partNo:'', modelType:'' })
const uploadDialog = reactive({ visible:false, partNo:'', modelType:'STRUCTURE', file:null })
const previewDialog = reactive({ visible:false, fileId:null, modelUrl:'', modelFormat:'', showViewer:false })

async function loadData() {
  loading.value = true
  try {
    const res = await pageModel3d(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function beforeUpload(file) {
  const exts = ['.step','.stp','.igs','.iges','.obj','.stl','.glb','.gltf','.sldprt','.f3d']
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!exts.includes(ext)) {
    ElMessage.warning('支持格式: STEP/IGS/OBJ/STL/GLB/SLDPRT')
    return false
  }
  uploadDialog.file = file
  uploadDialog.visible = true
  return false
}

async function doUpload() {
  if (!uploadDialog.partNo) { ElMessage.warning('请输入料号'); return }
  const fd = new FormData()
  fd.append('file', uploadDialog.file)
  fd.append('partNo', uploadDialog.partNo)
  fd.append('modelType', uploadDialog.modelType)
  submitting.value = true
  try {
    await uploadModel3d(fd)
    ElMessage.success('上传成功, 内网存高精度, 外网自动脱敏')
    uploadDialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleExplode(row) {
  const res = await explodeModel3d(row.id)
  ElMessage.success(res.message)
}

async function handleConvert(row) {
  convertingId.value = row.id
  try {
    const res = await convertToGlb(row.id)
    ElMessage.success(res.message || 'GLB转换完成，点击预览即可在线查看')
    loadData()
  } catch (e) {
    ElMessage.error('转换失败: ' + (e.message || '请检查算法服务是否正常'))
  } finally {
    convertingId.value = null
  }
}

async function handlePreview(row) {
  const res = await previewModel3d(row.id)
  previewDialog.fileId = res.data.fileId
  previewDialog.modelFormat = row.extranetFileId ? 'glb' : (row.sourceFormat || '')
  previewDialog.modelUrl = res.data.glbUrl || `/api/file/${res.data.fileId}/download`
  previewDialog.visible = true
}

function onPreviewOpen() {
  previewDialog.showViewer = true
}

function onPreviewClose() {
  previewDialog.showViewer = false
}

onMounted(loadData)
</script>
