<template>
  <div class="page-container">
    <OperationGuide module-key="sys-integration" />
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>外部系统对接状态</span>
          <el-button icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="sys in systems" :key="sys.systemCode">
          <el-card shadow="hover">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
              <strong>{{ sys.systemCode }}</strong>
              <el-tag :type="sys.connected ? 'success' : 'info'" effect="dark">
                {{ sys.connected ? '已连通' : '未连通' }}
              </el-tag>
            </div>
            <p style="color:#606266;font-size:13px;margin:4px 0">{{ sys.description }}</p>
            <p style="color:#909399;font-size:12px;margin:4px 0">地址: {{ sys.baseUrl }}</p>
            <p style="margin:8px 0">
              <el-tag size="small" :type="sys.enabled ? 'success' : 'warning'">{{ sys.enabled ? '已启用' : '模拟模式(预留)' }}</el-tag>
            </p>
            <el-button size="small" type="primary" @click="handleTest(sys.systemCode)">测试连接</el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><span>2D 模具图纸自动分解引擎</span></template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px"
        title="上传模具总装 DWG/DXF 文件, 系统自动解析图层分类为: 总装图/CNC模仁/EDM铜公/线割/水路/排气/模胚/散件 8类标准图纸" />
      <el-upload drag :before-upload="handleDecompose" :show-file-list="false" accept=".dxf,.dwg">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 DWG/DXF 文件到此处, 或<em>点击上传</em></div>
      </el-upload>

      <div v-if="decomposeResult" style="margin-top:16px">
        <el-descriptions title="分解结果" :column="3" border>
          <el-descriptions-item label="总实体数">{{ decomposeResult.total_entities }}</el-descriptions-item>
          <el-descriptions-item label="图层数">{{ decomposeResult.total_layers }}</el-descriptions-item>
          <el-descriptions-item label="分类图纸数">{{ decomposeResult.classified_count }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="decomposeResult.sheets" border style="margin-top:12px" max-height="300">
          <el-table-column prop="category_name" label="分类" min-width="200" />
          <el-table-column prop="layer" label="来源图层" width="150" />
          <el-table-column prop="entity_count" label="实体数" width="90" align="center" />
          <el-table-column label="尺寸标注" width="90" align="center">
            <template #default="{row}"><el-tag size="small" :type="row.has_dimension?'success':'info'">{{ row.has_dimension?'有':'无' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="公差" width="80" align="center">
            <template #default="{row}"><el-tag size="small" :type="row.has_tolerance?'warning':'info'">{{ row.has_tolerance?'有':'无' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="text_labels" label="文本标注" min-width="200" show-overflow-tooltip>
            <template #default="{row}">{{ (row.text_labels||[]).join(', ') }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { integrationStatus, testIntegration, decomposeDrawing } from '@/api/algorithm'

const systems = ref([])
const decomposeResult = ref(null)

async function loadData() {
  const res = await integrationStatus()
  systems.value = res.data
}

async function handleTest(code) {
  const res = await testIntegration(code)
  ElMessage.success(res.data ? `${code} 连接成功` : `${code} 连接失败`)
  loadData()
}

async function handleDecompose(file) {
  const fd = new FormData()
  fd.append('file', file)
  ElMessage.info('正在调用算法服务分解图纸...')
  try {
    const res = await decomposeDrawing(fd)
    decomposeResult.value = res.data
    ElMessage.success('图纸分解完成')
  } catch (e) {
    console.error(e)
  }
  return false
}

onMounted(loadData)
</script>
