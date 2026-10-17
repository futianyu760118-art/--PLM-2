<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <h3 class="title">产品全生命周期（12 环节 · 真实数据）</h3>
      <p class="sub">业务导航视图：以下数字均来自系统各模块实时统计{{ isAdmin ? '' : '（项目类口径=我的）' }}；不改变后端阶段门（G0–G8 以后端为准）。</p>
      <el-row :gutter="12">
        <el-col v-for="(s, i) in stages" :key="s.name" :xs="24" :sm="12" :md="8" :lg="6">
          <div class="stage-card">
            <div class="stage-head">
              <span class="idx">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="name">{{ s.name }}</span>
            </div>
            <div class="metrics">
              <router-link v-for="it in s.items" :key="it.label" :to="it.link" class="metric">
                <span class="v">{{ it.value }}<small>{{ it.unit }}</small></span>
                <span class="l">{{ it.label }}</span>
              </router-link>
            </div>
            <p class="scope">{{ s.scope }}</p>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { lifecycleOverview } from '@/api/lifecycle'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes('ADMIN'))
const stages = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    // 非管理员只统计本人相关（后端强制；管理员可看全局）
    const res = await lifecycleOverview(isAdmin.value ? {} : { mine: true })
    stages.value = res.data || []
  } finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.title { margin: 0 0 4px; font-size: 15px; }
.sub { margin: 0 0 14px; font-size: 12px; color: #909399; }
.stage-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px; margin-bottom: 12px; background: #fff;
  display: flex; flex-direction: column; height: calc(100% - 12px);
  .stage-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
    .idx { font-size: 12px; color: var(--plm-accent, #409eff); font-weight: 700; }
    .name { font-weight: 600; font-size: 14px; } }
  .metrics { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; flex: 1; }
  .metric { min-width: 78px; border-radius: 6px; background: color-mix(in srgb, var(--plm-accent, #409eff) 6%, #fff);
    border: 1px solid color-mix(in srgb, var(--plm-accent, #409eff) 16%, #e4e7ed);
    padding: 6px 8px; text-decoration: none; display: flex; flex-direction: column;
    transition: transform .12s cubic-bezier(.34,1.56,.64,1), box-shadow .15s ease;
    &:hover { box-shadow: 0 2px 8px rgba(64,158,255,.18); }
    &:active { transform: scale(.95); }
    .v { font-size: 18px; font-weight: 700; color: var(--plm-accent, #409eff); line-height: 1.1;
      small { font-size: 11px; font-weight: 400; margin-left: 2px; color: #909399; } }
    .l { font-size: 11px; color: #606266; margin-top: 2px; } }
  .scope { margin: 0; font-size: 11px; color: #b0b3bb; } }
</style>
