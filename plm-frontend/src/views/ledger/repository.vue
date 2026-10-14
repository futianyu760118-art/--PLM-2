<template>
  <div class="page-container">
    <OperationGuide module-key="ledgerRepository" />

    <el-card>
      <!-- 资料汇总处与台账同源同筛选：同一行数据的另一视图（AC-A3-3） -->
      <el-form :inline="true" :model="query">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="名称/编号/备注" clearable style="width:180px"
            @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="证据类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width:130px">
            <el-option v-for="t in options.types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-select v-model="query.ownerId" placeholder="全部" clearable filterable remote
            :remote-method="loadOwners" :loading="ownerLoading" style="width:140px">
            <el-option v-for="o in owners" :key="o.id" :label="o.realName || o.username" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源部门">
          <el-select v-model="query.deptId" placeholder="全部" clearable filterable style="width:150px">
            <el-option v-for="d in deptFlat" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
            <el-option v-for="s in options.statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联对象">
          <el-select v-model="query.objectType" placeholder="类型" clearable style="width:110px">
            <el-option v-for="o in options.objectTypes" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="query.objectKeyword" placeholder="编号/名称" clearable style="width:130px;margin-left:6px"
            @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker v-model="occurRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="起" end-placeholder="止" style="width:240px" @change="onRangeChange('occur')" />
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker v-model="uploadRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="起" end-placeholder="止" style="width:240px" @change="onRangeChange('upload')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <span class="tb-tip">共 {{ total }} 条证据；展开行按需加载该条证据下的资料清单</span>
        <span class="tb-tip right">允许格式 {{ limits.allowExts.join(' / ') }} · 单文件 ≤ {{ limits.maxFileSizeMb }}MB</span>
      </div>

      <!-- 展开行：按需拉附件清单，避免一次性拉全量附件 -->
      <el-table v-loading="loading" :data="tableData" border stripe row-key="id"
        @expand-change="onExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="att-box" v-loading="attLoading[row.id]">
              <el-table :data="attachments[row.id] || []" size="small" border>
                <el-table-column prop="fileName" label="资料名称" min-width="220" show-overflow-tooltip />
                <el-table-column label="大小" width="100">
                  <template #default="s">{{ humanSize(s.row.fileSize) }}</template>
                </el-table-column>
                <el-table-column label="上传人" width="110">
                  <template #default="s">{{ s.row.uploadedBy || '—' }}</template>
                </el-table-column>
                <el-table-column label="上传时间" width="160">
                  <template #default="s">{{ fmtTime(s.row.uploadedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="150">
                  <template #default="s">
                    <el-button link type="primary" size="small" @click="previewAttachment(s.row)">预览</el-button>
                    <el-button link type="primary" size="small" @click="downloadAttachment(s.row)">下载</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!attLoading[row.id] && !(attachments[row.id] || []).length"
                description="该证据暂无资料" :image-size="50" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ledgerNo" label="证据编号" width="175" fixed sortable />
        <el-table-column prop="name" label="证据名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="证据类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="责任人" width="90">
          <template #default="{ row }">{{ ownerLabel(row.ownerId) }}</template>
        </el-table-column>
        <el-table-column label="来源部门" width="130">
          <template #default="{ row }">{{ deptLabel(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="occurDate" label="发生时间" width="110" />
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.uploadedAt) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !tableData.length" description="无匹配数据" :image-size="70" />

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="pageSizes" layout="total, sizes, prev, pager, next, jumper"
          @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <el-drawer v-model="detail.visible" :title="'资料详情 · ' + (detail.ledger?.ledgerNo || '')" size="720px">
      <el-descriptions v-if="detail.ledger" :column="2" border size="small">
        <el-descriptions-item label="证据名称" :span="2">{{ detail.ledger.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="证据类型">{{ typeLabel(detail.ledger.type) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.ledger.status) }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ ownerLabel(detail.ledger.ownerId) }}</el-descriptions-item>
        <el-descriptions-item label="来源部门">{{ deptLabel(detail.ledger.deptId) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ detail.ledger.occurDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.ledger.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">资料清单</el-divider>
      <el-table :data="detail.attachments" size="small" border>
        <el-table-column prop="fileName" label="资料名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="previewAttachment(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="downloadAttachment(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">关联对象</el-divider>
      <el-table :data="detail.links" size="small" border>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ objectTypeLabel(row.objectType) }}</template>
        </el-table-column>
        <el-table-column prop="objectNo" label="编号" width="140" />
        <el-table-column prop="objectName" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.linkStatus === 'INVALID'" type="danger" size="small">
              失效({{ row.invalidReason === 'OBSOLETE' ? '已作废' : '已删除' }})
            </el-tag>
            <el-tag v-else type="success" size="small">有效</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="jump(row)">前往</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="previewDlg.visible" :title="previewDlg.name" width="860px" top="5vh" append-to-body>
      <img v-if="previewDlg.kind === 'image'" :src="previewDlg.url" class="lg-img" />
      <iframe v-else-if="previewDlg.kind === 'pdf'" :src="previewDlg.url" class="lg-frame" />
      <el-empty v-else description="该格式暂不支持在线预览，请下载查看" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDepartmentTree } from '@/api/system'
import {
  pageLedger, getLedger, ledgerOptions, searchLedgerOwners,
  downloadLedgerAttachment, fetchLedgerPreview
} from '@/api/ledger'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const owners = ref([])
const ownerLoading = ref(false)
const depts = ref([])
const attachments = reactive({})
const attLoading = reactive({})

const options = reactive({ types: [], statuses: [], objectTypes: [] })
const limits = reactive({ maxFileSizeMb: 200, allowExts: [], pageSizes: [20, 50, 100] })
const pageSizes = computed(() => limits.pageSizes?.length ? limits.pageSizes : [20, 50, 100])

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', type: '', ownerId: null, deptId: null,
  status: '', objectType: '', objectKeyword: '', occurStart: '', occurEnd: '', uploadStart: '', uploadEnd: '' })
const occurRange = ref([])
const uploadRange = ref([])
const detail = reactive({ visible: false, ledger: null, attachments: [], links: [] })
const previewDlg = reactive({ visible: false, name: '', kind: '', url: '' })

const typeLabel = (v) => options.types.find(t => t.value === v)?.label || v || '—'
const objectTypeLabel = (v) => options.objectTypes.find(o => o.value === v)?.label || v
const statusLabel = (s) => ({ DRAFT: '草稿', SUBMITTED: '已提交', ARCHIVED: '已归档', VOID: '作废' }[s] || s || '—')
const statusTag = (s) => ({ DRAFT: 'info', SUBMITTED: 'success', ARCHIVED: 'warning', VOID: 'danger' }[s] || 'info')
const ownerLabel = (id) => owners.value.find(o => o.id === id)?.realName || (id ? '#' + id : '—')
const deptLabel = (id) => deptFlat.value.find(d => d.id === id)?.label || (id ? '#' + id : '—')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')
const humanSize = (n) => {
  if (n == null) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

const deptFlat = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    for (const n of nodes || []) {
      out.push({ id: n.id, label: '　'.repeat(depth) + (n.deptName || n.deptCode) })
      walk(n.children, depth + 1)
    }
  }
  walk(depts.value, 0)
  return out
})

async function loadOptions() {
  const res = await ledgerOptions()
  const d = res.data || {}
  options.types = d.types || []
  options.statuses = d.statuses || []
  options.objectTypes = d.objectTypes || []
  Object.assign(limits, d.limits || {})
}

async function loadDepts() {
  try {
    const res = await getDepartmentTree()
    depts.value = res.data || []
  } catch { depts.value = [] }
}

async function loadOwners(keyword = '') {
  ownerLoading.value = true
  try {
    const res = await searchLedgerOwners(keyword, 20)
    owners.value = res.data || []
  } finally { ownerLoading.value = false }
}

async function load() {
  if (!validateRange()) return
  loading.value = true
  try {
    const res = await pageLedger(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

function validateRange() {
  if (query.occurStart && query.occurEnd && query.occurStart > query.occurEnd) {
    ElMessage.warning('起始时间不能大于结束时间'); return false
  }
  if (query.uploadStart && query.uploadEnd && query.uploadStart > query.uploadEnd) {
    ElMessage.warning('起始时间不能大于结束时间'); return false
  }
  return true
}

function onRangeChange(which) {
  const r = which === 'occur' ? occurRange.value : uploadRange.value
  query[which + 'Start'] = r?.[0] || ''
  query[which + 'End'] = r?.[1] || ''
}

function search() { query.pageNum = 1; load() }

function resetQuery() {
  Object.assign(query, { pageNum: 1, keyword: '', type: '', ownerId: null, deptId: null, status: '',
    objectType: '', objectKeyword: '', occurStart: '', occurEnd: '', uploadStart: '', uploadEnd: '' })
  occurRange.value = []
  uploadRange.value = []
  load()
}

async function onExpand(row, expanded) {
  const isOpen = Array.isArray(expanded) ? expanded.some(r => r.id === row.id) : !!expanded
  if (!isOpen || attachments[row.id]) return
  attLoading[row.id] = true
  try {
    const res = await getLedger(row.id)
    attachments[row.id] = res.data.attachments || []
  } finally { attLoading[row.id] = false }
}

async function openDetail(row) {
  const res = await getLedger(row.id)
  detail.ledger = res.data.ledger
  detail.attachments = res.data.attachments || []
  detail.links = res.data.links || []
  detail.visible = true
}

async function downloadAttachment(row) {
  try { await downloadLedgerAttachment(row.id, row.fileName) } catch { ElMessage.error('下载失败') }
}

async function previewAttachment(row) {
  try {
    const r = await fetchLedgerPreview(row.id)
    const kind = r.type.startsWith('image/') ? 'image' : r.type.includes('pdf') ? 'pdf' : 'other'
    previewDlg.name = row.fileName
    previewDlg.kind = kind
    previewDlg.url = r.url
    previewDlg.visible = true
  } catch { ElMessage.error('预览失败，请改用下载查看') }
}

function jump(row) { if (row.jumpPath) router.push(row.jumpPath) }

onMounted(async () => {
  await Promise.all([loadOptions(), loadDepts()])
  await loadOwners()
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  if (route.query.type) query.type = String(route.query.type)
  await load()
})
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex; align-items: center; margin-bottom: 10px;
  .tb-tip { color: #909399; font-size: 12px; }
  .tb-tip.right { margin-left: auto; }
}
.att-box { padding: 8px 12px; background: #fafafa; }
.lg-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.lg-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
</style>
