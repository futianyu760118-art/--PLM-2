<template>
  <div class="page-container">
    <OperationGuide module-key="project-progress" />
    <el-card>
      <el-form :inline="true">
        <el-form-item label="选择项目">
          <el-select v-model="projectId" filterable placeholder="选择研发项目" style="width:340px" @change="load">
            <el-option v-for="p in projects" :key="p.id"
              :label="`${p.projectNo || ''} ${p.projectName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button icon="Refresh" :disabled="!projectId" @click="load">刷新</el-button>
          <el-button type="primary" plain icon="CircleCheck" :disabled="!projectId" @click="doCheck">进度自检</el-button>
        </el-form-item>
      </el-form>

      <div v-if="summary" class="sum-bar">
        <el-tag type="success">完成 {{ summary.done }}/{{ summary.total }}</el-tag>
        <el-tag type="primary">关键节点 {{ summary.keyDone }}/{{ summary.keyTotal }}</el-tag>
        <el-tag type="warning">逾期 {{ summary.overdue }}</el-tag>
        <el-tag type="info">未设置 {{ summary.notSet }}</el-tag>
        <span class="rate">节点完成率 <b>{{ Math.round((summary.nodeRate || 0) * 100) }}%</b></span>
      </div>

      <el-table v-if="nodes.length" :data="nodes" row-key="id" border
        v-loading="loading" @expand-change="onExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="node-form">
              <el-form :inline="true" :model="row" label-width="90px">
                <el-form-item label="状态">
                  <el-select v-model="row.status" style="width:140px">
                    <el-option v-for="s in statusList" :key="s.v" :label="s.l" :value="s.v" />
                  </el-select>
                </el-form-item>
                <el-form-item label="计划日期">
                  <el-date-picker v-model="row.planDate" type="date" value-format="YYYY-MM-DD" style="width:150px" />
                </el-form-item>
                <el-form-item label="实际日期">
                  <el-date-picker v-model="row.actualDate" type="date" value-format="YYYY-MM-DD" style="width:150px" />
                </el-form-item>
                <el-form-item label="责任人"><el-input v-model="row.owner" style="width:120px" /></el-form-item>
                <el-form-item label="交付物"><el-input v-model="row.deliveryDesc" style="width:180px" /></el-form-item>
                <el-form-item label="备注"><el-input v-model="row.remark" style="width:200px" /></el-form-item>
                <el-form-item>
                  <el-button type="primary" size="small" :loading="savingId === row.id" @click="saveNode(row)">保存节点</el-button>
                </el-form-item>
              </el-form>

              <el-divider content-position="left">
                证据文件 ({{ (evidenceMap[row.id] || []).length }})
                <el-upload style="display:inline-block;margin-left:8px"
                  :show-file-list="false" :http-request="opt => doUpload(row, opt)" :disabled="uploadingId === row.id">
                  <el-button size="small" type="success" plain icon="Upload" :loading="uploadingId === row.id">上传证据</el-button>
                </el-upload>
                <el-button size="small" type="primary" plain icon="EditPen" style="margin-left:6px"
                  @click="openTextEvidence(row)">填写证据</el-button>
              </el-divider>
              <el-table :data="evidenceMap[row.id] || []" size="small" border empty-text="暂无证据文件">
                <el-table-column prop="fileName" label="证据物" min-width="200" show-overflow-tooltip />
                <el-table-column label="来源" width="80" align="center">
                  <template #default="{ row: ev }">
                    <el-tag size="small" :type="ev.source === 'TEXT' ? 'warning' : 'primary'">
                      {{ ev.source === 'TEXT' ? '填写' : '文件' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="docType" label="类型" width="120" />
                <el-table-column prop="note" label="说明" width="140" show-overflow-tooltip />
                <el-table-column prop="uploadedBy" label="上传人" width="90" />
                <el-table-column prop="uploadedAt" label="时间" width="165" />
                <el-table-column label="操作" width="200">
                  <template #default="{ row: ev }">
                    <el-button link type="primary" size="small" @click="previewEvidence(ev)">打开</el-button>
                    <el-button link type="warning" size="small" @click="openEditEvidence(ev)">编辑</el-button>
                    <el-button v-if="ev.fileId" link type="info" size="small" @click="downloadEvidence(ev.fileId, ev.fileName)">下载</el-button>
                    <el-button link type="danger" size="small" @click="removeEvidence(row, ev)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="std-tip">完成标准: 状态=已完成 且 填写实际完成日期 且 证据文件≥1</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="seq" label="#" width="50" align="center" />
        <el-table-column label="节点" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.isKey" type="danger" size="small" effect="dark" style="margin-right:4px">★</el-tag>
            {{ row.nodeName }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划日期" width="120" />
        <el-table-column prop="actualDate" label="实际日期" width="120" />
        <el-table-column prop="owner" label="责任人" width="110" />
        <el-table-column prop="deliveryDesc" label="交付物" min-width="160" show-overflow-tooltip />
        <el-table-column label="证据" width="80" align="center">
          <template #default="{ row }">
            <el-badge :value="row.evidenceCount || 0" :type="row.evidenceCount ? 'success' : 'info'" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="请选择项目查看进度节点" :image-size="100" />
    </el-card>

    <!-- 自检 -->
    <el-dialog v-model="checkVisible" title="项目进度自检" width="760px">
      <template v-if="check">
        <div class="check-head">
          <el-progress type="dashboard" :percentage="check.score" :width="120" :color="scoreColor(check.score)">
            <template #default="{ percentage }">
              <div class="score-num">{{ percentage }}</div>
              <div class="score-lbl">健康分</div>
            </template>
          </el-progress>
          <div class="metrics">
            <p>节点完成率 NCR: <b>{{ pct(check.metrics?.ncr) }}</b></p>
            <p>关键节点完成率 KCR: <b>{{ pct(check.metrics?.kcr) }}</b></p>
            <p>计划准时率 OTR: <b>{{ pct(check.metrics?.otr) }}</b></p>
            <p>数据完整率 CR: <b>{{ pct(check.metrics?.cr) }}</b></p>
            <p>
              问题: <el-tag type="danger" size="small">HIGH {{ check.bySeverity?.HIGH || 0 }}</el-tag>
              <el-tag type="warning" size="small" style="margin-left:6px">MEDIUM {{ check.bySeverity?.MEDIUM || 0 }}</el-tag>
            </p>
          </div>
        </div>
        <el-divider content-position="left">问题清单</el-divider>
        <el-table :data="check.issues" size="small" border max-height="320">
          <el-table-column prop="nodeName" label="节点" width="110" />
          <el-table-column label="关键" width="60" align="center">
            <template #default="{ row }"><span v-if="row.isKey">★</span></template>
          </el-table-column>
          <el-table-column label="级别" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="问题" />
        </el-table>
      </template>
    </el-dialog>

    <!-- 证据预览/打开 -->
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

    <!-- 填写/编辑证据 -->
    <el-dialog v-model="evForm.visible" :title="evForm.id ? '编辑证据' : '填写证据(系统内)'" width="640px">
      <el-form :model="evForm" label-width="70px">
        <el-form-item label="类型"><el-input v-model="evForm.docType" placeholder="如 PLAN / BOM / 测试报告" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="evForm.note" placeholder="简要说明" /></el-form-item>
        <el-form-item label="内容">
          <el-input v-model="evForm.content" type="textarea" :rows="9"
            placeholder="在系统内填写证据内容, 如: 检查记录 / 结论 / 测试数据 / 会议纪要" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="evForm.saving" @click="saveEvidenceForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageProject } from '@/api/project'
import { getNodeMatrix, updateNode, listEvidence, uploadEvidence, deleteEvidence, progressCheck, downloadEvidence, createTextEvidence, updateEvidence, fetchPreview, fetchTextEvidence } from '@/api/progress'

const projects = ref([])
const projectId = ref(null)
const nodes = ref([])
const summary = ref(null)
const loading = ref(false)
const savingId = ref(null)
const uploadingId = ref(null)
const evidenceMap = ref({})

const checkVisible = ref(false)
const check = ref({})

const preview = reactive({ visible: false, loading: false, kind: '', url: '', text: '', name: '', fileId: null })
const evForm = reactive({ visible: false, saving: false, id: null, nodeId: null, node: null, docType: '', note: '', content: '' })

const statusList = [
  { v: 'NOT_SET', l: '未设置' }, { v: 'PLANNED', l: '已计划' },
  { v: 'IN_PROGRESS', l: '进行中' }, { v: 'DONE', l: '已完成' },
  { v: 'FAILED', l: '未完成' }, { v: 'PENDING', l: '待定' }
]
const statusLabel = (s) => statusList.find(x => x.v === s)?.l || s
const statusTag = (s) => ({ DONE: 'success', IN_PROGRESS: 'warning', FAILED: 'danger', PENDING: 'info', PLANNED: 'primary' }[s] || 'info')
const pct = (v) => Math.round((v || 0) * 100) + '%'
const scoreColor = (s) => s >= 85 ? '#67c23a' : s >= 70 ? '#e6a23c' : '#f56c6c'

async function loadProjects() {
  const res = await pageProject({ pageNum: 1, pageSize: 200 })
  projects.value = res.data.records || []
  if (!projectId.value && projects.value.length) {
    projectId.value = projects.value[0].id
    await load()
  }
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await getNodeMatrix(projectId.value)
    nodes.value = res.data.nodes || []
    summary.value = res.data.summary || null
    evidenceMap.value = {}
  } finally { loading.value = false }
}

async function onExpand(row, expandedRows) {
  const isOpen = expandedRows.some(r => r.id === row.id)
  if (isOpen && !evidenceMap.value[row.id]) {
    await loadEvidences(row)
  }
}

async function loadEvidences(row) {
  const res = await listEvidence(row.id)
  evidenceMap.value = { ...evidenceMap.value, [row.id]: res.data || [] }
}

async function saveNode(row) {
  savingId.value = row.id
  try {
    await updateNode(projectId.value, row.nodeCode, {
      status: row.status, planDate: row.planDate, actualDate: row.actualDate,
      owner: row.owner, deliveryDesc: row.deliveryDesc, remark: row.remark
    })
    ElMessage.success('节点已保存')
    await load()
  } finally { savingId.value = null }
}

async function doUpload(row, opt) {
  uploadingId.value = row.id
  try {
    await uploadEvidence(row.id, opt.file, 'NODE_EVIDENCE', '')
    opt.onSuccess && opt.onSuccess()
    ElMessage.success('证据已上传')
    await loadEvidences(row)
    await load()
  } catch (e) {
    opt.onError && opt.onError(e)
  } finally { uploadingId.value = null }
}

async function removeEvidence(row, ev) {
  await ElMessageBox.confirm(`删除证据 [${ev.fileName}]?`, '警告', { type: 'warning' })
  await deleteEvidence(ev.id)
  ElMessage.success('已删除')
  await loadEvidences(row)
  await load()
}

async function previewEvidence(ev) {
  preview.visible = true
  preview.loading = true
  preview.name = ev.fileName
  preview.fileId = ev.fileId
  preview.kind = ''
  preview.url = ''
  preview.text = ''
  try {
    if (ev.source === 'TEXT' || !ev.fileId) {
      const d = await fetchTextEvidence(ev.id)
      preview.kind = 'text'
      preview.text = d.content || '(空)'
    } else {
      const r = await fetchPreview(ev.fileId)
      if (r.type.startsWith('image/')) { preview.kind = 'image'; preview.url = r.url }
      else if (r.type.includes('pdf')) { preview.kind = 'pdf'; preview.url = r.url }
      else if (r.type.startsWith('text/')) { preview.kind = 'text'; preview.text = r.text }
      else { preview.kind = 'other' }
    }
  } finally { preview.loading = false }
}

function openTextEvidence(row) {
  Object.assign(evForm, { visible: true, saving: false, id: null, nodeId: row.id, node: row, docType: row.nodeCode, note: '', content: '' })
}

function openEditEvidence(ev) {
  Object.assign(evForm, { visible: true, saving: false, id: ev.id, nodeId: ev.nodeId, node: null,
    docType: ev.docType, note: ev.note, content: ev.content || '' })
}

async function saveEvidenceForm() {
  if (!evForm.content || !evForm.content.trim()) { ElMessage.warning('请填写证据内容'); return }
  evForm.saving = true
  try {
    if (evForm.id) {
      await updateEvidence(evForm.id, { docType: evForm.docType, note: evForm.note, content: evForm.content })
      ElMessage.success('证据已更新')
    } else {
      await createTextEvidence(evForm.nodeId, { docType: evForm.docType, note: evForm.note, content: evForm.content })
      ElMessage.success('证据已填写')
    }
    evForm.visible = false
    if (evForm.node) await loadEvidences(evForm.node)
    else {
      const row = nodes.value.find(n => n.id === evForm.nodeId)
      if (row) await loadEvidences(row)
    }
    await load()
  } finally { evForm.saving = false }
}

async function doCheck() {
  const res = await progressCheck(projectId.value)
  check.value = res.data
  checkVisible.value = true
}

onMounted(loadProjects)
</script>

<style scoped lang="scss">
.sum-bar { display: flex; align-items: center; gap: 10px; margin: 10px 0;
  .rate { margin-left: auto; font-size: 13px; color: #606266; b { color: #409eff; } }
}
.node-form { padding: 6px 12px 12px; background: #fafafa; border-radius: 6px;
  .std-tip { margin-top: 8px; font-size: 12px; color: #e6a23c; }
}
.check-head { display: flex; align-items: center; gap: 30px;
  .score-num { font-size: 22px; font-weight: 700; }
  .score-lbl { font-size: 12px; color: #909399; }
  .metrics p { margin: 6px 0; font-size: 13px; color: #606266; b { color: #409eff; } }
}
.preview-text { max-height: 60vh; overflow: auto; background: #f7f8fa; border: 1px solid #eee;
  border-radius: 6px; padding: 14px; white-space: pre-wrap; font-size: 13px; line-height: 1.7; margin: 0; }
.preview-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.preview-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
</style>
