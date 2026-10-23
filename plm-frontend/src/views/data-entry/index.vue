<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="de-head">
          <span>统一录入中心</span>
          <span class="de-sub">在一个界面录入资料，自动映射到对应模块与流程标准</span>
        </div>
      </template>

      <div class="de-cats">
        <div v-for="c in categories" :key="c.key" class="de-cat" @click="go(c.path)">
          <el-icon class="de-ic" :size="22"><component :is="c.icon" /></el-icon>
          <div class="de-info">
            <div class="de-title">{{ c.title }}</div>
            <div class="de-desc">{{ c.desc }}</div>
          </div>
          <el-button size="small" type="primary" plain>录入</el-button>
        </div>
      </div>

      <el-divider content-position="left">归类查阅</el-divider>
      <el-tabs v-model="browseTab">
        <el-tab-pane label="项目" name="project">
          <el-table :data="projects" size="small" border stripe v-loading="loadingProject">
            <el-table-column prop="projectNo" label="项目编号" width="170" />
            <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="owner" label="负责人" width="100" />
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="阶段门" width="90"><template #default="{ row }">{{ row.currentGate || '—' }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="物料" name="material">
          <el-table :data="materials" size="small" border stripe v-loading="loadingMaterial">
            <el-table-column prop="partNo" label="料号" width="170" />
            <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="drawingNo" label="图纸编号" width="130" />
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="反馈" name="feedback">
          <el-table :data="feedbacks" size="small" border stripe v-loading="loadingFeedback">
            <el-table-column prop="id" label="#" width="60" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="fTag(row.status)">{{ fLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="submitter_name" label="提交人" width="90" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { pageProject } from '@/api/project'
import { pageMaterial } from '@/api/material'
import { listFeedback } from '@/api/feedback'

const router = useRouter()

const categories = [
  { key: 'initiation', title: '项目立项', desc: '新建研发项目立项申请', path: '/project/initiation', icon: 'DocumentAdd' },
  { key: 'tracking', title: '研发项目跟踪', desc: '节点进度 / 台账统一填报', path: '/project/tracking', icon: 'DataBoard' },
  { key: 'progress', title: '项目明细表', desc: '22 节点明细 / 证据', path: '/project/progress', icon: 'Tickets' },
  { key: 'worksheets', title: '项目工作表', desc: '规格书/配置表/评审/试产/出货', path: '/project/worksheets', icon: 'Memo' },
  { key: 'material', title: '物料建档', desc: '料号 / 参数 / 图纸', path: '/material/wizard', icon: 'Box' },
  { key: 'ledger', title: '证据台账', desc: '统一证据上传 / 归档', path: '/ledger/list', icon: 'Document' },
  { key: 'changes', title: '变更列表', desc: '项目变更留痕', path: '/project/changes', icon: 'Switch' },
  { key: 'bom', title: 'BOM结构', desc: '多级 BOM / 爆炸', path: '/bom/list', icon: 'Connection' },
  { key: 'model3d', title: '3D模型', desc: '3D 档案上传', path: '/model3d/list', icon: 'View' },
  { key: 'archive', title: '档案管理', desc: '档案目录树', path: '/archive/list', icon: 'FolderOpened' },
  { key: 'outsourcing', title: '外协发图', desc: '发图审批', path: '/outsourcing/list', icon: 'Promotion' }
]

const browseTab = ref('project')
const projects = ref([])
const materials = ref([])
const feedbacks = ref([])
const loadingProject = ref(false)
const loadingMaterial = ref(false)
const loadingFeedback = ref(false)

const fLabel = s => ({ pending: '待分析', proposed: '待确认方案', approved: '待实现', implementing: '实现中', implemented: '待闭环', resolved: '待管理员确认', verified: '待合并', merged: '已合并', closed: '已关闭' }[s] || s)
const fTag = s => ({ pending: 'warning', proposed: 'warning', approved: 'primary', implementing: 'primary', implemented: 'primary', resolved: 'warning', verified: 'primary', merged: 'success', closed: 'info' }[s] || 'info')

function go(path) { router.push(path) }

async function loadProject() {
  loadingProject.value = true
  try { const r = await pageProject({ pageNum: 1, pageSize: 10 }); projects.value = r.data.records || [] } finally { loadingProject.value = false }
}
async function loadMaterial() {
  loadingMaterial.value = true
  try { const r = await pageMaterial({ pageNum: 1, pageSize: 10 }); materials.value = r.data.records || [] } finally { loadingMaterial.value = false }
}
async function loadFeedback() {
  loadingFeedback.value = true
  try { const r = await listFeedback({ page: 1, pageSize: 10 }); feedbacks.value = r.data?.items || [] } finally { loadingFeedback.value = false }
}

watch(browseTab, (t) => {
  if (t === 'project' && !projects.value.length) loadProject()
  else if (t === 'material' && !materials.value.length) loadMaterial()
  else if (t === 'feedback' && !feedbacks.value.length) loadFeedback()
})
onMounted(loadProject)
</script>

<style scoped lang="scss">
.de-head { display: flex; align-items: baseline; gap: 12px;
  span:first-child { font-weight: 600; font-size: 15px; }
  .de-sub { font-size: 12px; color: #909399; }
}
.de-cats { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.de-cat { display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid #e4e7ed; border-radius: 10px;
  cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .15s; background: #fff;
  &:hover { border-color: var(--plm-accent, #409eff); box-shadow: 0 4px 14px rgba(64,158,255,.12); transform: translateY(-2px); }
  .de-ic { color: var(--plm-accent, #409eff); flex: 0 0 auto; }
  .de-info { flex: 1; min-width: 0;
    .de-title { font-weight: 600; font-size: 14px; }
    .de-desc { font-size: 12px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; } }
}
</style>
