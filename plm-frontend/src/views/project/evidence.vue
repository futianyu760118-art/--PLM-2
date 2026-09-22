<template>
  <div class="page-container">
    <OperationGuide module-key="project-evidence" />
    <el-card>
      <el-form :inline="true" :model="query">
        <el-form-item label="项目">
          <el-select v-model="query.projectId" filterable clearable placeholder="全部项目" style="width:220px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.projectNo || ''} ${p.projectName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点">
          <el-select v-model="query.nodeCode" clearable placeholder="全部" style="width:130px" @change="load">
            <el-option v-for="n in nodeOptions" :key="n.v" :label="n.l" :value="n.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.source" clearable placeholder="全部" style="width:110px" @change="load">
            <el-option label="文件" value="FILE" /><el-option label="填写" value="TEXT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="证据物/说明/节点" clearable style="width:180px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="load">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="6"><div class="stat"><div class="num">{{ stats.total || 0 }}</div><div class="lbl">证据总数</div></div></el-col>
        <el-col :span="6"><div class="stat"><div class="num">{{ stats.bySource?.FILE || 0 }}</div><div class="lbl">文件证据</div></div></el-col>
        <el-col :span="6"><div class="stat"><div class="num">{{ stats.bySource?.TEXT || 0 }}</div><div class="lbl">系统内填写</div></div></el-col>
        <el-col :span="6">
          <div class="stat">
            <div class="num">{{ stats.coverage != null ? Math.round(stats.coverage * 100) + '%' : '—' }}</div>
            <div class="lbl">节点覆盖率{{ stats.nodeTotal ? ` (${stats.nodeWithEvidence}/${stats.nodeTotal})` : '' }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="dim-row">
        <el-col :span="12">
          <el-card shadow="never" class="dim-card">
            <template #header>按类型分布</template>
            <el-tag v-for="(v, k) in stats.byDocType" :key="k" style="margin:3px">{{ k }} · {{ v }}</el-tag>
            <el-empty v-if="!stats.byDocType || !Object.keys(stats.byDocType).length" description="暂无" :image-size="50" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="dim-card">
            <template #header>按上传人分布</template>
            <el-tag v-for="(v, k) in stats.byUploader" :key="k" type="info" style="margin:3px">{{ k }} · {{ v }}</el-tag>
            <el-empty v-if="!stats.byUploader || !Object.keys(stats.byUploader).length" description="暂无" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="项目" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="nodeCode" label="节点" width="120" />
        <el-table-column prop="fileName" label="证据物" min-width="200" show-overflow-tooltip />
        <el-table-column label="来源" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.source === 'TEXT' ? 'warning' : 'primary'">
              {{ row.source === 'TEXT' ? '填写' : '文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="docType" label="类型" width="120" />
        <el-table-column prop="note" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="uploadedBy" label="上传人" width="90" />
        <el-table-column prop="uploadedAt" label="时间" width="165" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="previewEvidence(row)">打开</el-button>
            <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.fileId" link type="info" size="small" @click="downloadEvidence(row.fileId, row.fileName)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <el-dialog v-model="preview.visible" :title="'证据 · ' + (preview.name || '')" width="840px" top="5vh">
      <div v-if="preview.loading" style="text-align:center;padding:40px;color:#909399">加载中...</div>
      <template v-else>
        <pre v-if="preview.kind === 'text'" class="preview-text">{{ preview.text }}</pre>
        <img v-else-if="preview.kind === 'image'" :src="preview.url" class="preview-img" />
        <iframe v-else-if="preview.kind === 'pdf'" :src="preview.url" class="preview-frame"></iframe>
        <el-empty v-else description="该类型暂不支持在线预览, 请下载查看" :image-size="90" />
      </template>
      <template #footer>
        <el-button v-if="preview.fileId" type="primary" @click="downloadEvidence(preview.fileId, preview.name)">下载</el-button>
        <el-button @click="preview.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evForm.visible" title="编辑证据" width="640px">
      <el-form :model="evForm" label-width="70px">
        <el-form-item label="类型"><el-input v-model="evForm.docType" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="evForm.note" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="evForm.content" type="textarea" :rows="9"
          placeholder="系统内填写的证据内容(文件类证据可留空)" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="evForm.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageProject } from '@/api/project'
import { evidenceList, evidenceStats, updateEvidence, fetchPreview, fetchTextEvidence, downloadEvidence } from '@/api/progress'

const projects = ref([])
const projectMap = ref({})
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const stats = ref({})

const query = reactive({ pageNum: 1, pageSize: 20, projectId: null, nodeCode: '', source: '', keyword: '' })

const nodeOptions = [
  { v: 'PLAN', l: '计划表' }, { v: 'BOM', l: 'BOM' }, { v: 'SPEC', l: '规格书' },
  { v: 'CONFIG', l: '配置表' }, { v: 'MOLD_DRAWING', l: '模具图纸' }, { v: 'MOLD_REVIEW', l: '开模评审' },
  { v: 'HAND_SAMPLE', l: '手样' }, { v: 'MOLD', l: '模具' }, { v: 'MOLD_SAMPLE', l: '模样' },
  { v: 'PACKAGING', l: '包装设计' }, { v: 'ELEC_TRIAL', l: '电试' }, { v: 'RD_TRIAL', l: '研试' },
  { v: 'ENG_TRIAL', l: '工试' }, { v: 'PROD_TRIAL', l: '生试' }, { v: 'TEST_REPORT', l: '测试报告' },
  { v: 'TECH_TRANSFER', l: '技转' }, { v: 'SHIPMENT', l: '出货' }, { v: 'REVIEW', l: '复盘' }, { v: 'OTHER', l: '其他' }
]

const preview = reactive({ visible: false, loading: false, kind: '', url: '', text: '', name: '', fileId: null })
const evForm = reactive({ visible: false, saving: false, id: null, docType: '', note: '', content: '' })

const projectName = (id) => projectMap.value[id] || ('#' + id)

async function loadProjects() {
  const res = await pageProject({ pageNum: 1, pageSize: 200 })
  projects.value = res.data.records || []
  projects.value.forEach(p => { projectMap.value[p.id] = p.projectNo + ' ' + p.projectName })
}

async function load() {
  loading.value = true
  try {
    const res = await evidenceList(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
    const st = await evidenceStats({ projectId: query.projectId || undefined })
    stats.value = st.data || {}
  } finally { loading.value = false }
}

async function resetQuery() {
  Object.assign(query, { pageNum: 1, projectId: null, nodeCode: '', source: '', keyword: '' })
  await load()
}

async function previewEvidence(row) {
  Object.assign(preview, { visible: true, loading: true, kind: '', url: '', text: '', name: row.fileName, fileId: row.fileId })
  try {
    if (row.source === 'TEXT' || !row.fileId) {
      const d = await fetchTextEvidence(row.id)
      preview.kind = 'text'
      preview.text = d.content || '(空)'
    } else {
      const r = await fetchPreview(row.fileId)
      if (r.type.startsWith('image/')) { preview.kind = 'image'; preview.url = r.url }
      else if (r.type.includes('pdf')) { preview.kind = 'pdf'; preview.url = r.url }
      else if (r.type.startsWith('text/')) { preview.kind = 'text'; preview.text = r.text }
      else { preview.kind = 'other' }
    }
  } finally { preview.loading = false }
}

function openEdit(row) {
  Object.assign(evForm, { visible: true, saving: false, id: row.id, docType: row.docType, note: row.note, content: row.content || '' })
}

async function saveEdit() {
  evForm.saving = true
  try {
    await updateEvidence(evForm.id, { docType: evForm.docType, note: evForm.note, content: evForm.content })
    ElMessage.success('已保存')
    evForm.visible = false
    await load()
  } finally { evForm.saving = false }
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped lang="scss">
.stat-row { margin: 6px 0 12px;
  .stat { background: #f5f8ff; border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 16px; text-align: center;
    .num { font-size: 24px; font-weight: 700; color: #409eff; }
    .lbl { font-size: 12px; color: #909399; margin-top: 2px; }
  }
}
.dim-row { margin-bottom: 12px;
  .dim-card { :deep(.el-card__header) { padding: 8px 12px; font-size: 13px; color: #606266; }
    :deep(.el-card__body) { padding: 10px 12px; }
  }
}
.preview-text { max-height: 60vh; overflow: auto; background: #f7f8fa; border: 1px solid #eee;
  border-radius: 6px; padding: 14px; white-space: pre-wrap; font-size: 13px; line-height: 1.7; margin: 0; }
.preview-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.preview-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
</style>
