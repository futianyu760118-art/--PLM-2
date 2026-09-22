<template>
  <div class="page-container">
    <OperationGuide module-key="archive" />
    <el-card>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="料号">
          <el-input v-model="partNo" placeholder="输入料号查询档案" clearable @keyup.enter="loadTree" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadTree">查询</el-button>
          <el-button icon="FolderAdd" @click="handleGenerate" v-if="userStore.hasPermission('archive:upload')">生成目录树</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="!partNo" type="info" :closable="false" title="请输入料号查询产品固定档案目录树(17层标准目录)" />
    </el-card>

    <el-row :gutter="16" v-if="treeData.length" style="margin-top:16px">
      <el-col :span="10">
        <el-card>
          <template #header><span>档案目录树</span></template>
          <el-tree :data="treeData" :props="{label:'nodeName', children:'children'}" node-key="id"
            highlight-current default-expand-all @node-click="handleNodeClick">
            <template #default="{ node, data }">
              <span>
                <el-icon><Folder v-if="!data.isLeaf" /><Document v-else /></el-icon>
                {{ data.nodeName }}
                <el-badge v-if="nodeFilesCount[data.id]" :value="nodeFilesCount[data.id]" type="primary" style="margin-left:8px" />
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>{{ currentNode ? currentNode.nodeName : '文件列表' }}</span>
              <el-upload v-if="currentNode && userStore.hasPermission('archive:upload')" :show-file-list="false"
                :action="`/api/file/upload?partNo=${partNo}&fileType=archive`" :headers="uploadHeaders" :on-success="onUploadSuccess">
                <el-button size="small" type="primary" icon="Upload">上传文件</el-button>
              </el-upload>
            </div>
          </template>
          <el-empty v-if="!currentNode" description="请选择左侧目录节点" />
          <el-table v-else :data="nodeFiles" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="文件" min-width="200">
              <template #default="{row}">
                <el-link type="primary" :href="`/api/file/${row.fileId}/download`" target="_blank">文件 #{{ row.fileId }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="上传时间" min-width="160" />
            <el-table-column label="操作" width="100">
              <template #default="{row}">
                <el-button link type="danger" size="small" @click="handleRemoveFile(row)" v-if="userStore.hasPermission('archive:upload')">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getArchiveTree, generateArchive, getNodeFiles, attachFile, removeArchiveFile } from '@/api/archive'

const userStore = useUserStore()
const partNo = ref('')
const treeData = ref([])
const currentNode = ref(null)
const nodeFiles = ref([])
const nodeFilesCount = ref({})
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('plm_token') }))

async function loadTree() {
  if (!partNo.value) return
  const res = await getArchiveTree(partNo.value)
  treeData.value = res.data
  if (treeData.value.length) ElMessage.success(`已加载 ${treeData.value.length} 个目录节点`)
}

async function handleGenerate() {
  if (!partNo.value) { ElMessage.warning('请输入料号'); return }
  await ElMessageBox.confirm(`为料号[${partNo.value}]生成固定17层标准目录树?`, '确认', { type:'info' })
  await generateArchive(partNo.value)
  ElMessage.success('目录树已生成')
  loadTree()
}

async function handleNodeClick(data) {
  currentNode.value = data
  if (data.isLeaf) {
    const res = await getNodeFiles(data.id)
    nodeFiles.value = res.data
    nodeFilesCount.value[data.id] = res.data.length
  } else {
    nodeFiles.value = []
  }
}

async function onUploadSuccess(res) {
  if (res.code === 200 && currentNode.value) {
    await attachFile(partNo.value, currentNode.value.nodeCode, res.data.id)
    ElMessage.success('文件已挂载到目录')
    const r = await getNodeFiles(currentNode.value.id)
    nodeFiles.value = r.data
    nodeFilesCount.value[currentNode.value.id] = r.data.length
  }
}

async function handleRemoveFile(row) {
  await ElMessageBox.confirm('移除文件挂载?', '确认', { type:'warning' })
  await removeArchiveFile(row.id)
  ElMessage.success('已移除')
  if (currentNode.value) {
    const res = await getNodeFiles(currentNode.value.id)
    nodeFiles.value = res.data
  }
}
</script>
