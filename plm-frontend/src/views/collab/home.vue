<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <div class="head">
        <h3>项目协同 · 卡片墙{{ isAdmin ? '' : '（我的项目）' }}</h3>
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button value="mine">我的项目</el-radio-button>
          <el-radio-button v-if="isAdmin" value="all">全部项目</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="statusFilter" @change="load">
          <el-radio-button value="ACTIVE">进行中</el-radio-button>
          <el-radio-button value="ON_HOLD">暂停</el-radio-button>
          <el-radio-button value="MP">已量产</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-row :gutter="12">
        <el-col v-for="r in rows" :key="r.id" :xs="24" :sm="12" :md="8" :lg="6">
          <div class="pcard" @click="open(r)">
            <div class="top">
              <b class="no">{{ r.projectNo }}</b>
              <el-tag size="small" :type="statusTag(r.status)">{{ statusLabel(r.status) }}</el-tag>
            </div>
            <p class="name">{{ r.projectName }}</p>
            <div class="meta">
              <span>{{ r.owner || '—' }}</span><span>{{ r.currentStage || '—' }}</span>
            </div>
            <el-progress :percentage="Math.round((r.nodeDone / (r.nodeTotal || 1)) * 100)"
              :stroke-width="8" :color="r.overdue ? '#f56c6c' : undefined" />
            <div class="foot">
              <span>节点 {{ r.nodeDone }}/{{ r.nodeTotal }}</span>
              <span v-if="r.inProgress" class="ing">{{ r.inProgress }} 进行中</span>
              <span v-else class="muted">无进行中</span>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && !rows.length" description="暂无项目" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { pageTracking } from '@/api/project'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes('ADMIN'))
const rows = ref([])
const loading = ref(false)
const statusFilter = ref('ACTIVE')
const scope = ref('mine')

const statusLabel = (s) => ({ ACTIVE: '进行中', MP: '已量产', CLOSED: '已关闭', ON_HOLD: '暂停' }[s] || s)
const statusTag = (s) => ({ ACTIVE: 'primary', MP: 'success', CLOSED: 'info', ON_HOLD: 'warning' }[s] || 'info')

async function load() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 60, status: statusFilter.value || undefined }
    // 非管理员/我的项目：按负责人（tracking keyword 含 owner）筛选登录者本人
    const onlyMine = scope.value === 'mine' || !isAdmin.value
    if (onlyMine && userStore.realName) params.keyword = userStore.realName
    const res = await pageTracking(params)
    let list = res.data.records || []
    if (onlyMine && userStore.realName) list = list.filter((r) => r.owner === userStore.realName)
    rows.value = list
  } finally { loading.value = false }
}
function open(r) {
  router.push({ path: '/project/progress', query: { project: r.id } })
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.pcard { border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px; margin-bottom: 12px; cursor: pointer; background: #fff;
  transition: transform .12s cubic-bezier(.34,1.56,.64,1), box-shadow .15s ease;
  display: flex; flex-direction: column; gap: 8px; height: calc(100% - 12px); }
.pcard:hover { box-shadow: 0 4px 14px rgba(64,158,255,.18); }
.pcard:active { transform: scale(.97); }
.pcard .top { display: flex; justify-content: space-between; align-items: center; }
.pcard .no { font-size: 13px; color: var(--plm-accent, #409eff); }
.pcard .name { margin: 0; font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pcard .meta { display: flex; justify-content: space-between; font-size: 12px; color: #909399; }
.pcard .foot { display: flex; justify-content: space-between; font-size: 12px; color: #909399; }
.pcard .ing { color: #e6a23c; }
.muted { color: #c0c4cc; }
</style>
