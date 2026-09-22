<template>
  <el-container class="app-wrapper">
    <aside class="sidebar" :class="{ 'is-collapsed': isCollapse }">
      <div class="brand">
        <img src="@/assets/logo.svg" alt="logo" class="brand-logo" />
        <span v-show="!isCollapse" class="brand-text">恒剑 PLM</span>
        <el-icon class="brand-toggle" @click="isCollapse = !isCollapse">
          <Fold v-if="!isCollapse" />
          <Expand v-else />
        </el-icon>
      </div>

      <div class="nav-search" v-show="!isCollapse">
        <el-input v-model="navKeyword" placeholder="搜索模块" size="small" clearable prefix-icon="Search" />
      </div>

      <nav class="nav-scroll">
        <div v-for="group in visibleGroups" :key="group.stage" class="nav-group">
          <div v-show="!isCollapse" class="nav-group-head" @click="toggleStage(group.stage)">
            <span class="group-idx">{{ group.idx }}</span>
            <span class="group-name">{{ group.stage }}</span>
            <el-icon class="group-chev">
              <ArrowDown v-if="isStageOpen(group.stage)" />
              <ArrowRight v-else />
            </el-icon>
          </div>
          <div v-show="isCollapse || isStageOpen(group.stage)" class="nav-items">
            <el-tooltip v-for="item in group.items" :key="item.path" :content="item.title"
              placement="right" :disabled="!isCollapse" :show-after="120">
              <router-link :to="item.path" class="nav-item" :class="{ active: activeMenu === item.path }">
                <span class="nav-accent"></span>
                <el-icon class="nav-ic"><component :is="item.icon" /></el-icon>
                <span v-show="!isCollapse" class="nav-label">{{ item.title }}</span>
              </router-link>
            </el-tooltip>
          </div>
        </div>
        <div v-if="!visibleGroups.length" class="nav-empty">无匹配模块</div>
      </nav>

      <div class="sidebar-foot" v-show="!isCollapse">
        <span class="foot-dot"></span> PLM V4.0
      </div>
    </aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">工作台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentStage && currentStage !== '工作台'">{{ currentStage }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta?.title && currentStage !== '工作台'">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="30" :src="userStore.avatar">{{ userStore.realName?.charAt(0) }}</el-avatar>
              <span class="username">{{ userStore.realName }}</span>
              <el-tag size="small" type="info" effect="plain">{{ roleLabel }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive>
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const route = useRoute()
const routerPush = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const navKeyword = ref('')
const openStages = ref([])

// 工序前后顺序(上游 → 下游)
const STAGE_ORDER = [
  '工作台', '项目与物料', '产品结构', '3D与档案', '工程变更',
  '模具与工序', '品质检验', '外协协同', '数据治理', '度量分析',
  '导出集成', '智能改善', '系统管理'
]
const STAGE_ICONS = {
  '工作台': 'HomeFilled', '项目与物料': 'Flag', '产品结构': 'Connection',
  '3D与档案': 'FolderOpened', '工程变更': 'Switch', '模具与工序': 'SetUp',
  '品质检验': 'CircleCheckFilled', '外协协同': 'Promotion', '数据治理': 'DataLine',
  '度量分析': 'TrendCharts', '导出集成': 'Upload', '智能改善': 'MagicStick',
  '系统管理': 'Setting'
}

const activeMenu = computed(() => route.meta?.activeMenu || route.path)

const filteredRoutes = computed(() => {
  return router.options.routes.filter(r => {
    if (r.hidden || r.path === '/login') return false
    if (r.meta?.roles && !r.meta.roles.some(role => userStore.roles.includes(role))) return false
    if (r.meta?.permission && !(userStore.hasPermission(r.meta.permission) || userStore.roles.includes('ADMIN'))) return false
    return true
  })
})

function fullPath(route, child) {
  const base = route.path.endsWith('/') ? route.path.slice(0, -1) : route.path
  return base + '/' + child.path
}

// 按工序阶段分组为扁平菜单项
const groupedMenuRoutes = computed(() => {
  const groups = new Map()
  for (const r of filteredRoutes.value) {
    const stage = r.meta?.workflowStage || '其他'
    if (!groups.has(stage)) groups.set(stage, { stage, icon: STAGE_ICONS[stage] || r.meta?.icon, items: [] })
    const g = groups.get(stage)
    for (const c of (r.children || [])) {
      if (c.meta?.hidden) continue
      g.items.push({
        path: fullPath(r, c),
        title: c.meta?.title || r.meta?.title,
        icon: c.meta?.icon || r.meta?.icon || g.icon
      })
    }
  }
  const ordered = STAGE_ORDER.filter(s => groups.has(s)).map(s => groups.get(s))
    .concat(Array.from(groups.keys()).filter(s => !STAGE_ORDER.includes(s)).map(s => groups.get(s)))
  ordered.forEach((g, i) => { g.idx = String(i + 1).padStart(2, '0') })
  return ordered
})

const isSearching = computed(() => navKeyword.value.trim().length > 0)

const visibleGroups = computed(() => {
  const groups = groupedMenuRoutes.value
  if (!isSearching.value) return groups
  const kw = navKeyword.value.trim().toLowerCase()
  return groups
    .map(g => ({ ...g, items: g.items.filter(i =>
      i.title.toLowerCase().includes(kw) || g.stage.toLowerCase().includes(kw)) }))
    .filter(g => g.items.length)
})

function isStageOpen(stage) {
  if (isSearching.value) return true
  return openStages.value.includes(stage)
}
function toggleStage(stage) {
  const i = openStages.value.indexOf(stage)
  if (i >= 0) openStages.value.splice(i, 1)
  else openStages.value.push(stage)
}

const currentStage = computed(() => {
  for (const r of filteredRoutes.value) {
    if (route.path.startsWith(r.path + '/') || (r.path !== '/' && route.path === r.path)) {
      return r.meta?.workflowStage || ''
    }
  }
  return ''
})

// 路由变化/初始化时确保当前工序阶段展开
function ensureStageOpen() {
  const s = currentStage.value
  if (s && s !== '工作台' && !openStages.value.includes(s)) openStages.value.push(s)
}
watch(currentStage, async () => { await nextTick(); ensureStageOpen() })
onMounted(ensureStageOpen)

const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', ENGINEER: '工程师', QUALITY: '品质', SALES: '销售', SUPPLIER: '外协', CUSTOMER: '客户' }
  return map[userStore.roles[0]] || userStore.roles[0] || ''
})

async function handleCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗?', '提示', { type: 'warning' })
    try { await userStore.logout() }
    finally { routerPush.push('/login') }
  }
}
</script>

<style lang="scss" scoped>
.app-wrapper { height: 100vh; }

.sidebar {
  width: 244px;
  background: #0d1117;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  display: flex; flex-direction: column;
  transition: width 0.24s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  &.is-collapsed { width: 60px; }
}

.brand {
  height: 52px; flex: 0 0 52px;
  display: flex; align-items: center; gap: 9px;
  padding: 0 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  .brand-logo { width: 24px; height: 24px; flex: 0 0 24px; }
  .brand-text {
    font-size: 14px; font-weight: 600; color: #e6edf3;
    letter-spacing: 0.02em; white-space: nowrap; flex: 1;
  }
  .brand-toggle {
    color: #6e7681; font-size: 16px; cursor: pointer; border-radius: 6px;
    padding: 3px; transition: all 0.15s; flex: 0 0 auto;
    &:hover { color: #e6edf3; background: rgba(177, 186, 196, 0.12); }
  }
}

.nav-search {
  padding: 10px 10px 6px;
  :deep(.el-input__wrapper) {
    background: #161b22; box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) inset;
    border-radius: 6px;
    &:hover { box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset; }
    &.is-focus { box-shadow: 0 0 0 1px #388bfd inset; }
  }
  :deep(.el-input__inner) { color: #c9d1d9; font-size: 12px; &::placeholder { color: #6e7681; } }
  :deep(.el-input__prefix) { color: #6e7681; }
}

.nav-scroll {
  flex: 1; overflow-y: auto; overflow-x: hidden;
  padding: 4px 8px 12px;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.14) transparent;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.14); border-radius: 3px; }
}

.nav-group { margin-top: 4px; }

.nav-group-head {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 8px 5px;
  font-size: 11px; font-weight: 600; letter-spacing: 0.08em;
  color: #6e7681; text-transform: uppercase;
  cursor: pointer; user-select: none;
  transition: color 0.15s;
  &:hover { color: #c9d1d9; }
  .group-idx { font-size: 10px; color: #484f58; font-variant-numeric: tabular-nums; }
  .group-name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .group-chev { font-size: 12px; color: #484f58; }
}

.nav-items { display: flex; flex-direction: column; }

.nav-item {
  position: relative;
  display: flex; align-items: center; gap: 10px;
  padding: 7px 10px 7px 14px;
  margin: 1px 0;
  border-radius: 6px;
  color: #8b949e; font-size: 13px; text-decoration: none;
  white-space: nowrap; overflow: hidden;
  transition: background 0.14s, color 0.14s;
  .nav-accent {
    position: absolute; left: 3px; top: 50%; transform: translateY(-50%);
    width: 2px; height: 0; border-radius: 2px; background: #58a6ff;
    transition: height 0.16s ease;
  }
  .nav-ic { font-size: 15px; flex: 0 0 15px; }
  .nav-label { overflow: hidden; text-overflow: ellipsis; }
  &:hover { background: rgba(177, 186, 196, 0.1); color: #e6edf3; }
  &.active {
    background: rgba(56, 139, 253, 0.16);
    color: #58a6ff; font-weight: 500;
    .nav-accent { height: 15px; }
  }
}

.nav-empty { padding: 26px 8px; text-align: center; color: #484f58; font-size: 12px; }

.sidebar-foot {
  flex: 0 0 auto; height: 34px;
  display: flex; align-items: center; gap: 6px;
  padding: 0 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 11px; color: #484f58; letter-spacing: 0.04em;
  .foot-dot { width: 6px; height: 6px; border-radius: 50%; background: #3fb950; box-shadow: 0 0 6px rgba(63, 185, 80, 0.6); }
}

// 折叠为 icon-rail
.sidebar.is-collapsed {
  .brand { justify-content: center; padding: 0; .brand-toggle { display: none; } }
  .nav-scroll { padding: 6px; }
  .nav-item {
    justify-content: center; padding: 9px 0; gap: 0;
    .nav-accent { left: 0; }
  }
}

.header {
  background: #fff; display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  .header-left { display: flex; align-items: center; gap: 16px; }
  .header-right { display: flex; align-items: center; }
  .user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
  .username { font-size: 14px; }
}

.main-content { background: #f0f2f5; padding: 0; overflow: auto; }
.fade-transform-enter-active, .fade-transform-leave-active { transition: all 0.3s; }
.fade-transform-enter-from { opacity: 0; transform: translateX(-20px); }
.fade-transform-leave-to { opacity: 0; transform: translateX(20px); }
</style>
