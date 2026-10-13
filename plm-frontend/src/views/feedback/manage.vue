<template>
  <div class="page-container">
    <el-card>
      <div class="kpi-band">
        <div v-for="k in kpis" :key="k.key" class="kpi" :class="k.accent" @click="filterStatus = k.filter">
          <div class="num">{{ stats[k.key] ?? 0 }}</div>
          <div class="lbl">{{ k.label }}</div>
        </div>
      </div>

      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="标题/页面/提交人/回复人" clearable style="width:240px"
          @keyup.enter="search" @clear="search" />
        <el-select v-model="query.type" clearable placeholder="全部类型" style="width:110px" @change="search">
          <el-option v-for="(v, k) in TYPE_META" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.severity" clearable placeholder="全部严重度" style="width:120px" @change="search">
          <el-option v-for="(v, k) in SEVERITY_META" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.aiStatus" clearable placeholder="全部AI状态" style="width:130px" @change="search">
          <el-option v-for="(v, k) in AI_STATUS_META" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-radio-group v-model="filterStatus" @change="onFilterStatus">
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button v-for="(v, k) in STATUS_META" :key="k" :value="k">{{ v.label }}</el-radio-button>
        </el-radio-group>
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="data" border stripe size="small">
        <el-table-column label="标题" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">#{{ row.id }} {{ row.title }}</el-link>
            <div class="sub">{{ row.page_path || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" :type="tag(TYPE_META[row.type])">{{ label(TYPE_META[row.type]) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="严重度" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" :type="tag(SEVERITY_META[row.severity])">{{ label(SEVERITY_META[row.severity]) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" :type="tag(STATUS_META[row.status])">{{ label(STATUS_META[row.status]) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="AI" width="70" align="center">
          <template #default="{ row }">
            <el-tooltip :content="label(AI_STATUS_META[row.ai_status]) || '未派单'">
              <span class="ai-lamp" :class="'ai-' + (row.ai_status || 'none')" />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="submitter_name" label="提交人" width="90" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmt(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="回复人" width="90">
          <template #default="{ row }">{{ row.replied_by || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="canHandle && aiAction(row)" link size="small" :type="aiActionType(row)" @click="onAi(row)">{{ aiAction(row) }}</el-button>
            <el-button v-if="canHandle" link type="danger" size="small" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <el-drawer v-model="detail.visible" :title="detail.data ? '#' + detail.data.id + ' ' + detail.data.title : ''" size="720px">
      <template v-if="detail.data">
        <div class="tag-row">
          <el-tag size="small" :type="tag(TYPE_META[detail.data.type])">{{ label(TYPE_META[detail.data.type]) }}</el-tag>
          <el-tag size="small" :type="tag(SEVERITY_META[detail.data.severity])">{{ label(SEVERITY_META[detail.data.severity]) }}严重度</el-tag>
          <el-tag size="small" :type="tag(STATUS_META[detail.data.status])">{{ label(STATUS_META[detail.data.status]) }}</el-tag>
        </div>

        <el-divider content-position="left">反馈内容</el-divider>
        <div class="desc">{{ detail.data.description || '（无描述）' }}</div>
        <div v-if="(detail.data.attachments || []).length" class="atts">
          <el-tag v-for="a in detail.data.attachments" :key="a.key" closable type="info" @click="downloadAtt(a)" @close="noop">
            {{ a.name }}
          </el-tag>
        </div>

        <el-divider content-position="left">状态流转</el-divider>
        <div class="status-row">
          <span>当前：{{ label(STATUS_META[detail.data.status]) }}</span>
          <el-button v-for="b in statusButtons(detail.data.status)" :key="b.text" size="small"
            :type="b.type" @click="changeStatus(b.status, !!b.confirm)">{{ b.text }}</el-button>
        </div>

        <template v-if="detail.data.ai_status && detail.data.ai_status !== 'none'">
          <el-divider content-position="left">AI 实现</el-divider>
          <ai-section :detail="detail.data" :can-handle="canHandle" @ai="onAi" />
        </template>

        <template v-if="detail.data.reply">
          <el-divider content-position="left">处理回复</el-divider>
          <div class="reply-box">
            <div class="reply-meta">{{ detail.data.replied_by }} · {{ fmt(detail.data.replied_at) }}</div>
            <pre class="reply-text">{{ detail.data.reply }}</pre>
          </div>
        </template>

        <template v-if="canHandle">
          <el-divider content-position="left">回复提交人（可同步状态）</el-divider>
          <el-form label-width="80px">
            <el-form-item label="回复内容">
              <el-input v-model="replyForm.reply" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="填写处理结论或回复内容…" />
            </el-form-item>
            <el-form-item label="同步状态">
              <el-select v-model="replyForm.status" clearable placeholder="保持当前状态不变" style="width:180px">
                <el-option v-for="(v, k) in STATUS_META" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-button type="primary" :loading="replying" @click="doReply">回复并保存</el-button>
          </el-form>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listFeedback, replyFeedback, changeFeedbackStatus, deleteFeedback,
  aiQueueFeedback, aiCancelFeedback, downloadFeedbackFile
} from '@/api/feedback'
import AiSection from '@/components/feedback/AiSection.vue'

const userStore = useUserStore()
const canHandle = computed(() => userStore.hasPermission('feedback:manage'))

const TYPE_META = { issue: { label: '问题', type: 'danger' }, suggestion: { label: '建议', type: 'success' }, inquiry: { label: '咨询', type: 'primary' } }
const SEVERITY_META = { low: { label: '低', type: 'info' }, medium: { label: '中', type: 'warning' }, high: { label: '高', type: 'danger' } }
const STATUS_META = { pending: { label: '待处理', type: 'warning' }, processing: { label: '处理中', type: 'primary' }, resolved: { label: '已解决', type: 'success' }, closed: { label: '已关闭', type: 'info' } }
const AI_STATUS_META = { none: { label: '未派单', type: 'info' }, queued: { label: '排队中', type: 'warning' }, running: { label: '实现中', type: 'primary' }, done: { label: '已实现', type: 'success' }, failed: { label: '失败', type: 'danger' } }

const loading = ref(false)
const data = ref([])
const total = ref(0)
const stats = ref({ total: 0, pending: 0, processing: 0, resolved: 0, closed: 0 })
const query = reactive({ page: 1, pageSize: 20, keyword: '', type: '', severity: '', aiStatus: '' })
const filterStatus = ref('')

const detail = reactive({ visible: false, data: null })
const replyForm = reactive({ reply: '', status: '' })
const replying = ref(false)
const aiBusyId = ref(null)
let refreshTimer = null

const kpis = [
  { key: 'pending', label: '待处理', accent: 'warn', filter: 'pending' },
  { key: 'processing', label: '处理中', accent: 'info', filter: 'processing' },
  { key: 'resolved', label: '已解决', accent: 'ok', filter: 'resolved' },
  { key: 'closed', label: '已关闭', accent: 'gray', filter: 'closed' },
  { key: 'total', label: '反馈总数', accent: 'brand', filter: '' }
]

const tag = m => (m && m.type) || 'info'
const label = m => (m && m.label) || ''
const noop = () => {}
const fmt = t => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, page_size: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.type) params.type = query.type
    if (query.severity) params.severity = query.severity
    if (query.aiStatus) params.ai_status = query.aiStatus
    if (filterStatus.value) params.status = filterStatus.value
    const res = await listFeedback(params)
    data.value = res.data.items || []
    total.value = res.data.total || 0
    stats.value = res.data.stats || stats.value
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; load() }
function onFilterStatus() { query.page = 1; load() }

function openDetail(row) {
  detail.data = row
  detail.visible = true
  Object.assign(replyForm, { reply: '', status: '' })
}

function statusButtons(s) {
  const btns = []
  if (canHandle.value) {
    if (s === 'pending') { btns.push({ text: '开始处理', status: 'processing', type: 'default' }, { text: '标记已解决', status: 'resolved', type: 'primary', confirm: true }, { text: '关闭反馈', status: 'closed', type: 'danger', confirm: true }) }
    else if (s === 'processing') { btns.push({ text: '标记已解决', status: 'resolved', type: 'primary', confirm: true }, { text: '关闭反馈', status: 'closed', type: 'danger', confirm: true }) }
    else if (s === 'resolved') { btns.push({ text: '重开处理', status: 'processing', type: 'default' }, { text: '关闭反馈', status: 'closed', type: 'danger', confirm: true }) }
    else if (s === 'closed') { btns.push({ text: '重新打开', status: 'pending', type: 'default' }) }
  } else if (s === 'resolved') {
    btns.push({ text: '确认关闭', status: 'closed', type: 'primary', confirm: true })
  } else if (s === 'closed') {
    btns.push({ text: '重新打开', status: 'pending', type: 'default' })
  }
  return btns
}

async function changeStatus(status, needConfirm) {
  if (needConfirm) {
    await ElMessageBox.confirm(`确认将 #${detail.data.id} 更新为「${label(STATUS_META[status])}」？`, '更新状态', { type: 'warning' })
  }
  const res = await changeFeedbackStatus(detail.data.id, status)
  detail.data = res.data
  ElMessage.success('状态已更新')
  load()
}

async function doReply() {
  if (!replyForm.reply && !replyForm.status) { ElMessage.warning('请填写回复内容或选择状态'); return }
  replying.value = true
  try {
    const res = await replyFeedback(detail.data.id, replyForm)
    detail.data = res.data
    Object.assign(replyForm, { reply: '', status: '' })
    ElMessage.success('已保存')
    load()
  } finally {
    replying.value = false
  }
}

function aiAction(r) {
  if (r.ai_status === 'queued') return '取消排队'
  if (r.status === 'resolved' || r.status === 'closed') return ''
  if (r.ai_status === 'running') return '重新排队'
  if (r.ai_status === 'failed') return '重试'
  if (r.ai_status === 'done') return ''
  return '交 AI 实现'
}
function aiActionType(r) {
  if (r.ai_status === 'queued') return 'warning'
  return 'primary'
}

async function onAi(r) {
  aiBusyId.value = r.id
  try {
    let res
    if (r.ai_status === 'queued') { res = await aiCancelFeedback(r.id); ElMessage.success('已取消排队') }
    else { res = await aiQueueFeedback(r.id); ElMessage.success('已派给 AI，等待 runner 处理') }
    if (detail.data && detail.data.id === r.id) detail.data = res.data
    load()
  } catch (e) { /* 拦截器已提示 */ } finally { aiBusyId.value = null }
}

function confirmDelete(r) {
  ElMessageBox.confirm(`确认删除反馈 #${r.id} ${r.title}？`, '删除反馈', { type: 'warning' })
    .then(async () => { await deleteFeedback(r.id); ElMessage.success('已删除'); if (detail.data?.id === r.id) detail.visible = false; load() })
    .catch(() => {})
}

function downloadAtt(a) {
  const fileId = a.key
  if (fileId && /^\d+$/.test(String(fileId))) downloadFeedbackFile(fileId, a.name)
  else ElMessage.info('该附件无本地文件可下载')
}

function setupAutoRefresh() {
  clearInterval(refreshTimer)
  const hasActive = data.value.some(r => r.ai_status === 'queued' || r.ai_status === 'running')
  if (hasActive) refreshTimer = setInterval(load, 15000)
}

onMounted(() => { load(); refreshTimer = setInterval(() => { setupAutoRefresh() }, 5000) })
onBeforeUnmount(() => clearInterval(refreshTimer))
</script>

<style scoped lang="scss">
.kpi-band { display: flex; gap: 12px; margin-bottom: 12px;
  .kpi { flex: 1; background: #f5f8ff; border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 16px; cursor: pointer;
    .num { font-size: 24px; font-weight: 700; color: #409eff; }
    .lbl { font-size: 12px; color: #909399; margin-top: 2px; }
    &.warn .num { color: #e6a23c; } &.ok .num { color: #67c23a; } &.gray .num { color: #909399; } &.info .num { color: #409eff; }
  }
}
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.sub { font-size: 12px; color: #909399; }
.ai-lamp { display: inline-block; width: 12px; height: 12px; border-radius: 50%; background: #c0c4cc;
  &.ai-queued { background: #e6a23c; } &.ai-running { background: #409eff; box-shadow: 0 0 0 3px rgba(64,158,255,.2); }
  &.ai-done { background: #67c23a; } &.ai-failed { background: #f56c6c; }
}
.tag-row { display: flex; gap: 8px; }
.desc { white-space: pre-wrap; color: #334155; line-height: 1.8; margin-bottom: 8px; }
.atts { display: flex; gap: 6px; flex-wrap: wrap; }
.status-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.reply-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 12px;
  .reply-meta { font-size: 12px; color: #909399; }
  .reply-text { margin: 6px 0 0; white-space: pre-wrap; font-family: inherit; font-size: 13px; }
}
</style>
