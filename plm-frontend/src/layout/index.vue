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
            <el-breadcrumb-item v-if="currentStage && currentStage !== '工作台'" :to="{ path: currentStagePath }">{{ currentStage }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta?.title && currentStage !== '工作台'">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-modes" role="tablist" aria-label="界面模式 · 同一项目，同一份数据">
          <span class="modes-title">界面模式<i>同一项目 · 同一份数据</i></span>
          <el-tooltip v-for="m in MODE_LIST" :key="m.key" :content="m.desc" placement="bottom">
            <button class="mode-btn" :class="{ on: appearance.mode === m.key }" role="tab"
              :aria-selected="appearance.mode === m.key" @click="switchMode(m.key)">
              <b>{{ m.label }}</b><small>{{ m.desc }}</small>
            </button>
          </el-tooltip>
        </div>
        <div class="header-right">
          <el-tooltip content="外观设置（强调色/底色/密度）" placement="bottom">
            <button class="agent-entry" aria-label="外观设置" @click="themeVisible = true">
              <el-icon :size="16"><Brush /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="帮助与学习" placement="bottom">
            <button class="agent-entry" aria-label="帮助与学习" @click="routerPush.push('/help')">
              <el-icon :size="16"><QuestionFilled /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="恒剑PLM智能体" placement="bottom">
            <button class="agent-entry" @click="agentChatVisible = true">
              <el-icon :size="16"><ChatDotRound /></el-icon>
              <span class="agent-entry-label">PLM智能体</span>
            </button>
          </el-tooltip>
          <el-dropdown v-if="!isAdminRole" @command="switchScope">
            <button class="agent-entry" aria-label="数据范围">
              <el-icon :size="16"><View /></el-icon>
              <span class="agent-entry-label">{{ scopeLabel }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="1" :disabled="scope === 1">看全部</el-dropdown-item>
                <el-dropdown-item command="2" :disabled="scope === 2">只看别人</el-dropdown-item>
                <el-dropdown-item command="3" :disabled="scope === 3">只看自己</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

    <!-- 全局反馈入口:单点注入,所有走 Layout 的页面自动覆盖(AC F1) -->
    <FeedbackFab />
    <!-- 恒剑PLM智能体对话抽屉(右上角入口) -->
    <AgentChat v-model="agentChatVisible" />
    <!-- 外观设置抽屉 -->
    <ThemePanel v-model="themeVisible" />
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import FeedbackFab from '@/components/feedback/FeedbackFab.vue'
import AgentChat from '@/components/agent/AgentChat.vue'
import ThemePanel from '@/components/appearance/ThemePanel.vue'
import { useAppearanceStore } from '@/stores/appearance'
import { MODES, MODE_LIST } from '@/config/modes'
import { myScope, setMyScope } from '@/api/me'
import router from '@/router'

const agentChatVisible = ref(false)
const themeVisible = ref(false)
const appearance = useAppearanceStore()

// 其它页面（如 Multica 风格首页）请求打开总控对话
window.addEventListener('plm-open-agent-chat', () => { agentChatVisible.value = true })

// URL ?mode= 同步（白名单校验；非法参数忽略）
watch(() => route.query.mode, (m) => {
  if (m && MODES[m] && m !== appearance.mode) appearance.setMode(m)
}, { immediate: true })

function switchMode(key) {
  appearance.setMode(key)
  router.replace({ query: { ...route.query, mode: key } }).catch(() => {})
  // 点击模式即跳转到该模式主页(项目协同→看板, 产品空间→工作空间…)
  const home = MODES[key]?.home || '/dashboard'
  if (route.path !== home) routerPush.push(home)
}

const route = useRoute()
const routerPush = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const navKeyword = ref('')
const openStages = ref([])

const isAdminRole = computed(() => userStore.roles.includes('ADMIN'))
const scope = ref(1)
const scopeLabel = computed(() => ({ 1: '看全部', 2: '只看别人', 3: '只看自己' }[scope.value] || '看全部'))
async function loadScope() {
  try { const r = await myScope(); scope.value = r?.data?.dataScope ?? 1 } catch { scope.value = 1 }
}
async function switchScope(v) {
  try { await setMyScope(Number(v)); scope.value = Number(v); window.location.reload() } catch { /* 忽略 */ }
}
loadScope()

// 工序前后顺序(上游 → 下游)
const STAGE_ORDER = [
  '工作台', '研发自治中心', '项目与物料', '产品结构', '3D与档案', '证据台账',
  '订阅与报告', '工程变更', '模具与工序', '品质检验', '外协协同', '数据治理', '度量分析',
  '导出集成', '智能改善', '系统管理'
]
const STAGE_ICONS = {
  '工作台': 'HomeFilled', '研发自治中心': 'DataBoard', '项目与物料': 'Flag',
  '产品结构': 'Connection',
  '3D与档案': 'FolderOpened', '证据台账': 'Document', '订阅与报告': 'Bell',
  '工程变更': 'Switch', '模具与工序': 'SetUp',
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

// 当前用户可访问的完整路径集合（用于模式分组过滤：只显示登录者有权限的入口）
const accessiblePaths = computed(() => {
  const set = new Set(['/dashboard', '/help', '/lifecycle'])
  for (const r of filteredRoutes.value) {
    for (const c of r.children || []) set.add(fullPath(r, c))
  }
  return set
})

const visibleGroups = computed(() => {
  // 非经典模式：使用模式自带分组（仅重组导航，业务页面/权限不变；按登录者权限过滤入口）
  const modeDef = MODES[appearance.mode]
  if (modeDef && modeDef.groups) {
    const groups = modeDef.groups.map((g, i) => ({
      stage: g.stage, icon: g.icon, idx: String(i + 1).padStart(2, '0'),
      items: g.items.map((it) => ({ ...it })).filter((it) => accessiblePaths.value.has(it.path))
    })).filter((g) => g.items.length)
    if (!isSearching.value) return groups
    const kw = navKeyword.value.trim().toLowerCase()
    return groups
      .map((g) => ({ ...g, items: g.items.filter((i) =>
        i.title.toLowerCase().includes(kw) || g.stage.toLowerCase().includes(kw)) }))
      .filter((g) => g.items.length)
  }
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

// 工序阶段的落地页(面包屑点击返回该阶段列表)
const currentStagePath = computed(() => {
  const s = currentStage.value
  if (!s || s === '工作台') return '/'
  for (const r of filteredRoutes.value) {
    if (r.meta?.workflowStage === s) {
      if (r.redirect) return r.redirect
      if (r.children?.length) return fullPath(r, r.children[0])
      return r.path
    }
  }
  return '/'
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
  .agent-entry { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 12px;
    margin-right: 14px; border: 1px solid #dcdfe6; background: #f5f7ff; color: #409eff;
    border-radius: 16px; font-size: 13px; cursor: pointer;
    transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.2s ease, background-color 0.25s ease;
    &:hover { background: #ecf5ff; box-shadow: 0 2px 8px rgba(64, 158, 255, 0.25); }
    &:active { transform: scale(0.95); } }
  .user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
  .username { font-size: 14px; }
}

.main-content { background: var(--plm-bg, #f0f2f5); padding: 0; overflow: auto; }
.fade-transform-enter-active, .fade-transform-leave-active { transition: all 0.3s; }
.fade-transform-enter-from { opacity: 0; transform: translateX(-20px); }
.fade-transform-leave-to { opacity: 0; transform: translateX(20px); }

// ===== 模式条与外观（AEOS 五模式，对齐 demo V6.2） =====
.header .header-modes { display: flex; align-items: center; gap: 6px; margin-right: auto; margin-left: 18px; flex-wrap: wrap;
  .modes-title { font-size: 12px; color: #909399; white-space: nowrap; margin-right: 4px;
    i { font-style: normal; color: #c0c4cc; margin-left: 4px; font-size: 11px; } }
  .mode-btn { border: 1px solid #e4e7ed; background: #fff; color: #606266; font-size: 12px;
    height: 34px; padding: 0 10px; border-radius: 8px; cursor: pointer; white-space: nowrap;
    display: inline-flex; flex-direction: column; align-items: flex-start; justify-content: center; line-height: 1.25;
    transition: all .18s cubic-bezier(.34,1.56,.64,1);
    b { font-weight: 600; font-size: 12px; }
    small { font-size: 10px; color: #a8abb2; }
    &:hover { border-color: var(--plm-accent, #409eff); color: #303133; transform: translateY(-1px); }
    &.on { background: var(--plm-accent, #409eff); border-color: var(--plm-accent, #409eff); color: #fff;
      small { color: rgba(255,255,255,.75); } } } }

// 侧栏变体：用户可选(跟随模式/浅色/深色)，由 html[data-sidebar] 驱动
:global(html[data-sidebar='light']) .sidebar { background: #fff; border-right: 1px solid #e5eaf2;
  .brand-text, .nav-item, .nav-item:hover { color: #243952; }
  .nav-item.active { background: color-mix(in srgb, var(--plm-accent, #409eff) 12%, #fff); color: var(--plm-accent, #409eff); }
  .nav-group-head { color: #8a94a6; .group-idx { color: #b3bccb; } }
  .sidebar-foot { color: #b3bccb; border-color: #eef2f8; } }
:global(html[data-sidebar='gray']) .sidebar { background: #f8f8fa; border-right: 1px solid #e9e9ee;
  .brand-text, .nav-item, .nav-item:hover { color: #30343a; }
  .nav-item.active { background: color-mix(in srgb, var(--plm-accent, #5867c7) 12%, #fff); color: var(--plm-accent, #5867c7); }
  .nav-group-head { color: #878a92; .group-idx { color: #c3c5cb; } }
  .sidebar-foot { color: #c3c5cb; border-color: #ececef; } }

// 强调色令牌（主按钮/选中态/进度）
:global(.el-button--primary) { --el-button-bg-color: var(--plm-accent, #409eff);
  --el-button-border-color: var(--plm-accent, #409eff);
  --el-button-hover-bg-color: var(--plm-accent, #409eff);
  --el-button-active-bg-color: var(--plm-accent, #409eff); }
:global(.el-menu-item.is-active), :global(.el-radio-button__original-radio:checked + .el-radio-button__inner) { color: var(--plm-accent, #409eff); }

// 密度
:global(html[data-density='compact']) .page-container { padding: 10px; }
:global(html[data-density='compact']) .el-table--small { font-size: 12px; }

// 减少动态效果
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
</style>
