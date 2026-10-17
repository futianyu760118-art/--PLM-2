<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header><span>Multica 风格 · 同一项目数据，紧凑分组列表呈现</span></template>
      <div v-for="g in groups" :key="g.key" class="group">
        <div class="g-head" :class="g.cls">{{ g.label }} <b>{{ g.items.length }}</b></div>
        <div v-for="r in g.items" :key="r.id" class="wi" @click="open(r)">
          <span class="dot" :class="g.cls" />
          <span class="no">{{ r.projectNo }}</span>
          <span class="name">{{ r.projectName }}</span>
          <span class="meta">{{ r.owner }}</span>
          <span class="meta">{{ r.currentStage }}</span>
          <span class="prog">{{ r.nodeDone }}/{{ r.nodeTotal }}</span>
        </div>
        <p v-if="!g.items.length" class="muted">无</p>
      </div>
      <el-empty v-if="!loading && !total" description="暂无项目" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageTracking } from '@/api/project'

const router = useRouter()
const rows = ref([])
const loading = ref(false)
const total = computed(() => rows.value.length)

const groups = computed(() => [
  { key: 'ACTIVE', label: '进行中', cls: 'primary', items: rows.value.filter((r) => r.status === 'ACTIVE') },
  { key: 'ON_HOLD', label: '暂停', cls: 'warn', items: rows.value.filter((r) => r.status === 'ON_HOLD') },
  { key: 'MP', label: '已量产', cls: 'success', items: rows.value.filter((r) => r.status === 'MP') },
  { key: 'CLOSED', label: '已关闭', cls: 'gray', items: rows.value.filter((r) => r.status === 'CLOSED') }
])

async function load() {
  loading.value = true
  try {
    const res = await pageTracking({ pageNum: 1, pageSize: 200 })
    rows.value = res.data.records || []
  } finally { loading.value = false }
}
function open(r) { router.push({ path: '/project/progress', query: { project: r.id } }) }
onMounted(load)
</script>

<style scoped>
.group { margin-bottom: 14px; }
.g-head { font-size: 12px; font-weight: 600; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
.g-head.primary { color: #409eff; } .g-head.warn { color: #e6a23c; } .g-head.success { color: #67c23a; } .g-head.gray { color: #909399; }
.wi { display: flex; align-items: center; gap: 10px; padding: 7px 10px; border-radius: 6px; font-size: 13px; cursor: pointer; }
.wi:hover { background: #f7f8fa; }
.wi .no { font-family: monospace; color: var(--plm-accent, #409eff); flex: 0 0 150px; }
.wi .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wi .meta { font-size: 12px; color: #909399; flex: 0 0 64px; }
.wi .prog { font-size: 12px; color: #606266; font-family: monospace; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.dot.primary { background: #409eff; } .dot.warn { background: #e6a23c; } .dot.success { background: #67c23a; } .dot.gray { background: #c0c4cc; }
.muted { color: #c0c4cc; font-size: 12px; }
</style>
