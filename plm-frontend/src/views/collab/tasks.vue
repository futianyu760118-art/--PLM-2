<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>协同任务（我的待办）</h3>
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPEN">进行中</el-radio-button>
          <el-radio-button value="ESCALATED">已升级</el-radio-button>
          <el-radio-button value="DONE">已完成</el-radio-button>
        </el-radio-group>
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="items" border stripe size="small">
        <el-table-column prop="title" label="任务" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DONE' ? 'success' : row.status === 'ESCALATED' ? 'danger' : 'primary'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="类型" width="120" />
        <el-table-column prop="dueDate" label="截止" width="110">
          <template #default="{ row }">{{ (row.dueDate || '').substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="165">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'DONE'" link type="success" size="small" @click="complete(row)">完成</el-button>
            <span v-else class="done-mark">✓</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !items.length" description="暂无待办任务" :image-size="70" />
      <p class="note">说明：完成待办不等于阶段门放行（DONE ≠ Gate 通过）；工序可能联动，以后端为准。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { myWorkItems, completeWorkItem } from '@/api/workitem'

const items = ref([])
const loading = ref(false)
const status = ref('')

const statusLabel = (s) => ({ OPEN: '进行中', ESCALATED: '已升级', DONE: '已完成' }[s] || s)
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

async function load() {
  loading.value = true
  try {
    const res = await myWorkItems(status.value || undefined)
    items.value = res.data || []
  } finally { loading.value = false }
}

async function complete(row) {
  await completeWorkItem(row.id)
  ElMessage.success('待办已完成')
  await load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.note { margin: 10px 0 0; font-size: 12px; color: #909399; }
.done-mark { color: #67c23a; font-weight: 700; }
</style>
