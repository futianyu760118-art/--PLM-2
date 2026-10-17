<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header><span>专业视图集 · 同一项目数据，按岗位分组呈现</span></template>
      <div v-for="g in groups" :key="g.owner" class="pg">
        <div class="pg-head">
          <el-icon><User /></el-icon><b>{{ g.owner || '未分配' }}</b>
          <span class="cnt">{{ g.items.length }} 个项目</span>
          <span v-if="g.overdue" class="od">逾期 {{ g.overdue }}</span>
        </div>
        <el-table :data="g.items" border stripe size="small">
          <el-table-column prop="projectNo" label="项目编号" width="150" />
          <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="阶段" width="80" align="center">
            <template #default="{ row }"><el-tag size="small">{{ row.currentStage }}</el-tag></template>
          </el-table-column>
          <el-table-column label="节点" width="90" align="center">
            <template #default="{ row }">{{ row.nodeDone }}/{{ row.nodeTotal }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }"><el-tag size="small" :type="tag(row.status)">{{ label(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="open(row)">明细</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-if="!loading && !groups.length" description="暂无项目" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageTracking } from '@/api/project'

const router = useRouter()
const groups = ref([])
const loading = ref(false)
const label = (s) => ({ ACTIVE: '进行中', MP: '已量产', CLOSED: '已关闭', ON_HOLD: '暂停' }[s] || s)
const tag = (s) => ({ ACTIVE: 'primary', MP: 'success', CLOSED: 'info', ON_HOLD: 'warning' }[s] || 'info')

async function load() {
  loading.value = true
  try {
    const res = await pageTracking({ pageNum: 1, pageSize: 200 })
    const rows = res.data.records || []
    const map = {}
    for (const r of rows) {
      const key = r.owner || '未分配'
      if (!map[key]) map[key] = []
      map[key].push(r)
    }
    groups.value = Object.entries(map).map(([owner, items]) => ({
      owner,
      items,
      overdue: items.filter((i) => i.overdueCells && Object.keys(i.overdueCells).length).length
    }))
  } finally { loading.value = false }
}
function open(row) { router.push({ path: '/project/progress', query: { project: row.id } }) }
onMounted(load)
</script>

<style scoped>
.pg { margin-bottom: 16px; }
.pg-head { display: flex; align-items: center; gap: 6px; font-size: 14px; margin-bottom: 6px; }
.pg-head .cnt { font-size: 12px; color: #909399; }
.pg-head .od { font-size: 12px; color: #f56c6c; }
</style>
