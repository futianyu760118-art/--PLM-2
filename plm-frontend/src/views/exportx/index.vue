<template>
  <div class="page-container">
    <OperationGuide module-key="exportx" />
    <el-card>
      <template #header><span>导出中心</span></template>
      <div class="table-toolbar">
        <el-button type="primary" @click="quickExport('part')">导出物料清单</el-button>
        <el-button type="success" @click="exportReleasedFinished">导出已发布成品清单</el-button>
        <el-button @click="quickExport('ecn')">导出ECN清单</el-button>
        <el-button @click="quickExport('metric')">导出度量值</el-button>
        <el-button @click="loadList">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="tasks" border stripe size="small">
        <el-table-column prop="exportNo" label="编号" width="180" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="exportType" label="类型" width="90" />
        <el-table-column prop="format" label="格式" width="60" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="70" align="right" />
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{row}">
            <el-button link size="small" type="primary" v-if="row.status==='DONE'" @click="doDownload(row.id)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createExport, listExports } from '@/api/exportx'

const loading = ref(false)
const tasks = ref([])
const statusTag = (s) => ({ DONE: 'success', FAILED: 'danger', RUNNING: 'warning', QUEUED: 'info' }[s] || 'info')

async function loadList() {
  loading.value = true
  try {
    const res = await listExports()
    tasks.value = res.data || []
  } finally { loading.value = false }
}

async function quickExport(type) {
  const nameMap = { part: '物料清单', ecn: 'ECN清单', metric: '度量值' }
  const res = await createExport({ exportType: type === 'metric' ? 'METRIC' : 'RESOURCE', format: 'csv', name: nameMap[type], spec: { resource: type } })
  if (res.data.status === 'DONE') {
    ElMessage.success(`导出完成: ${res.data.rowCount} 行`)
  } else if (res.data.errorMsg) {
    ElMessage.error('导出失败: ' + res.data.errorMsg)
  }
  loadList()
}

// 验收剧本：导出已发布成品清单，走系统导出不走直连库
async function exportReleasedFinished() {
  const res = await createExport({
    exportType: 'RESOURCE', format: 'csv', name: '已发布成品清单',
    spec: { resource: 'part', status: 'RELEASED', materialType: 'FINISHED' }
  })
  if (res.data.status === 'DONE') {
    ElMessage.success(`导出完成: ${res.data.rowCount} 行`)
    if (res.data.filePath) window.open(`/api/v1/exports/${res.data.id}/download`, '_blank')
  } else if (res.data.errorMsg) {
    ElMessage.error('导出失败: ' + res.data.errorMsg)
  }
  loadList()
}

function doDownload(id) {
  window.open(`/api/v1/exports/${id}/download`, '_blank')
}

onMounted(loadList)
</script>
