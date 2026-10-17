<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>产品工作空间</h3>
        <el-select v-model="projectKey" filterable placeholder="选择产品/项目" style="width: 340px" @change="loadFacts">
          <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="`${p.projectNo || ''} ${p.projectName}`" />
        </el-select>
        <el-button icon="Refresh" :disabled="!projectId" @click="loadFacts">刷新</el-button>
      </div>

      <el-empty v-if="!projectId" description="选择一个项目/产品，聚合展示其研发上下文" :image-size="80" />

      <template v-if="project">
        <el-descriptions :column="4" border size="small" class="facts">
          <el-descriptions-item label="项目编号">{{ project.projectNo }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ project.projectName }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ project.owner }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ project.status }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ project.currentGate }}</el-descriptions-item>
          <el-descriptions-item label="关联料号">{{ project.partNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="节点进度">{{ card ? `${card.nodes.done}/${card.nodes.total} 完成` : '—' }}</el-descriptions-item>
          <el-descriptions-item label="证据">{{ card ? `${card.evidenceCount} 条` : '—' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="card">
          <h4 class="sec">工作表数据（点击进入对应工作表）</h4>
          <div class="sheet-grid">
            <router-link v-for="s in SHEET_DEFS" :key="s.type" class="sheet-cell"
              :to="{ path: '/project/worksheets', query: { type: s.type, projectNo: project.projectNo } }">
              <span class="n">{{ card.sheets[s.type] ?? 0 }}</span>
              <span class="t">{{ s.label }}</span>
            </router-link>
          </div>

          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <h4 class="sec">变更与立项</h4>
              <div class="mini-facts">
                <div class="mini" @click="go('/ecn/list')">
                  <span class="n">{{ card.ecnCount }}</span><span class="t">ECN 变更单</span>
                </div>
                <div class="mini" @click="go('/project/initiation')">
                  <span class="n">{{ card.initiation.length ? '已关联' : '无' }}</span>
                  <span class="t">立项 {{ card.initiation.length ? card.initiation[0].init_no : '' }}</span>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :md="12">
              <h4 class="sec">阶段门流转（最近）</h4>
              <el-timeline v-if="card.gates.length" class="gates">
                <el-timeline-item v-for="g in card.gates" :key="g.gate_code + g.actual_date"
                  :type="g.result === 'PASS' ? 'success' : 'danger'" :timestamp="g.actual_date" placement="top">
                  {{ g.gate_code }} {{ g.gate_name }} — {{ g.result }} <span class="op">/ {{ g.operator }}</span>
                </el-timeline-item>
              </el-timeline>
              <p v-else class="muted">暂无阶段门流转记录</p>
            </el-col>
          </el-row>
        </template>

        <div class="quick">
          <el-button v-for="q in QUICK" :key="q.to" @click="go(q.to)">{{ q.label }}</el-button>
        </div>
        <p class="note">以上为只读聚合；操作与详情进入对应模块完成，权限与业务规则以后端为准。</p>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { pageProject } from '@/api/project'
import { lifecycleProjectCard } from '@/api/lifecycle'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes('ADMIN'))
const projects = ref([])
const projectId = ref(null)
const project = ref(null)
const card = ref(null)
const projectKey = ref(null)

const SHEET_DEFS = [
  { type: 'plan', label: '计划表' }, { type: 'basebom', label: '基础BOM' }, { type: 'spec', label: '规格书' },
  { type: 'config', label: '配置表' }, { type: 'sample', label: '样品单' }, { type: 'review', label: '评审单' },
  { type: 'test', label: '测试报告' }, { type: 'trial', label: '试产报告' }, { type: 'shipment', label: '出货' }
]

const QUICK = [
  { label: '项目明细表', to: '/project/progress' },
  { label: '研发项目跟踪', to: '/project/tracking' },
  { label: '项目工作表', to: '/project/worksheets' },
  { label: 'BOM结构', to: '/bom/list' },
  { label: '工程变更', to: '/ecn/list' },
  { label: '档案', to: '/archive/list' }
]

async function init() {
  const res = await pageProject({ pageNum: 1, pageSize: 200 })
  let list = res.data.records || []
  // 非管理员：产品空间只显示登录者负责的项目
  if (!isAdmin.value && userStore.realName) list = list.filter((p) => p.owner === userStore.realName)
  projects.value = list
  if (projects.value.length) {
    projectId.value = projectKey.value = projects.value[0].id
    await loadFacts()
  }
}

async function loadFacts() {
  projectId.value = projectKey.value
  project.value = projects.value.find((p) => p.id === projectId.value) || null
  card.value = null
  if (!project.value) return
  try {
    const res = await lifecycleProjectCard(projectId.value)
    card.value = res.data || null
  } catch { card.value = null }
}

function go(to) {
  router.push({ path: to, query: to === '/project/progress' ? { project: projectId.value } : {} })
}

init()
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.facts { margin-bottom: 6px; }
.sec { margin: 14px 0 8px; font-size: 13px; color: #606266; }
.sheet-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 8px; }
.sheet-cell { border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px 6px; text-align: center; text-decoration: none;
  display: flex; flex-direction: column; gap: 2px; background: #fff;
  transition: transform .12s cubic-bezier(.34,1.56,.64,1), box-shadow .15s ease; }
.sheet-cell:hover { box-shadow: 0 2px 8px rgba(64,158,255,.16); }
.sheet-cell:active { transform: scale(.95); }
.sheet-cell .n { font-size: 20px; font-weight: 700; color: var(--plm-accent, #409eff); }
.sheet-cell .t { font-size: 12px; color: #606266; }
.mini-facts { display: flex; gap: 10px; }
.mini { flex: 1; border: 1px dashed #dcdfe6; border-radius: 8px; padding: 10px; text-align: center; cursor: pointer; }
.mini:hover { border-color: var(--plm-accent, #409eff); }
.mini .n { font-size: 16px; font-weight: 700; color: var(--plm-accent, #409eff); display: block; }
.mini .t { font-size: 12px; color: #909399; }
.gates { padding: 4px 0 0 4px; }
.gates .op { color: #909399; font-size: 12px; }
.muted { color: #c0c4cc; font-size: 13px; }
.quick { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.note { margin: 10px 0 0; font-size: 12px; color: #909399; }
</style>
