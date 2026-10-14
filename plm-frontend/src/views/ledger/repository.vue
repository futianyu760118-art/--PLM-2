<template>
  <div class="page-container">
    <OperationGuide module-key="ledgerRepository" />

    <el-card>
      <!-- 资料汇总处与台账同源同筛选：同一行数据的另一视图（AC-A3-3、AC-B6-7） -->
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
        <el-form-item label="来源证据编号">
          <el-input v-model="query.ledgerNoLike" placeholder="EV-…" clearable style="width:150px"
            @keyup.enter="search" />
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
        <el-form-item label="上传时间">
          <el-date-picker v-model="uploadRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="起" end-placeholder="止" style="width:240px" @change="onRangeChange('upload')" />
        </el-form-item>
        <el-form-item label="变更时间">
          <el-date-picker v-model="modifyRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="起" end-placeholder="止" style="width:240px" @change="onRangeChange('modify')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 分类维度筛选(AC-B6-1): 同维度多选取「或」, 跨维度取「与」 -->
      <el-form v-if="filterDims.length" :inline="true" label-width="auto">
        <el-form-item v-for="dim in filterDims" :key="dim.id" :label="dim.dimName">
          <el-select v-model="categorySel[dim.id]" multiple collapse-tags collapse-tags-tooltip clearable
            placeholder="全部" style="width:190px" @change="search">
            <el-option v-for="v in dim.values" :key="v.id" :label="v.valueName" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button link type="primary" @click="clearCategory">清空分类条件</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <span class="tb-tip">共 {{ total }} 条资料；展开行按需加载该条资料下的附件清单</span>
        <span class="tb-tip right">允许格式 {{ limits.allowExts.join(' / ') }} · 单文件 ≤ {{ limits.maxFileSizeMb }}MB</span>
      </div>

      <!-- 展开行：按需拉附件清单，避免一次性拉全量附件 -->
      <el-table v-loading="loading" :data="tableData" border stripe row-key="ledgerId"
        :row-class-name="rowClass" @expand-change="onExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="att-box" v-loading="attLoading[row.ledgerId]">
              <el-table :data="attachments[row.ledgerId] || []" size="small" border>
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
              <el-empty v-if="!attLoading[row.ledgerId] && !(attachments[row.ledgerId] || []).length"
                description="该资料暂无附件" :image-size="50" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ledgerNo" label="来源证据编号" width="180" fixed sortable />
        <el-table-column prop="name" label="资料名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="资料类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="分类标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="c in row.categories" :key="c" size="small" class="cat-tag" effect="plain">{{ c }}</el-tag>
            <span v-if="!row.categories?.length" class="muted">—</span>
          </template>
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
        <el-table-column label="变更时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.lastModifiedAt) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" size="small" :disabled="!row.files?.length"
              @click="downloadFirst(row)">下载</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="无匹配数据" :image-size="70" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="pageSizes" layout="total, sizes, prev, pager, next, jumper"
          @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <el-drawer v-model="detail.visible" :title="'资料详情 · ' + (detail.ledgerNo || '')" size="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="资料名称" :span="2">{{ detail.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="资料类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ ownerLabel(detail.ownerId) }}</el-descriptions-item>
        <el-descriptions-item label="来源部门">{{ deptLabel(detail.deptId) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ detail.occurDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version || '—' }}</el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ fmtTime(detail.uploadedAt) }}</el-descriptions-item>
        <el-descriptions-item label="变更时间">{{ fmtTime(detail.lastModifiedAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">分类标签</el-divider>
      <!-- 一条资料可打多个维度标签，均在详情可见(AC-B1-6) -->
      <div class="cat-box">
        <el-tag v-for="c in detail.categories" :key="c" size="small" effect="plain" class="cat-tag">{{ c }}</el-tag>
        <span v-if="!detail.categories?.length" class="muted">暂无分类标签</span>
        <el-button link type="primary" size="small" class="cat-edit" @click="openTagDlg">打标签</el-button>
      </div>

      <el-divider content-position="left">资料附件</el-divider>
      <el-table :data="detail.files" size="small" border>
        <el-table-column prop="fileName" label="附件名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="!row.previewable"
              @click="previewFile(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="downloadFile(row)">下载</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="muted">该资料暂无附件</span>
        </template>
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

      <el-divider content-position="left">订阅</el-divider>
      <div class="sub-box">
        <el-button v-if="!detail.subscribed" type="primary" size="small" @click="toggleSubscribe(detail)">
          订阅本条变更
        </el-button>
        <template v-else>
          <el-tag type="success" size="small">已订阅 {{ detail.subNo }}</el-tag>
          <el-button link type="danger" size="small" @click="toggleSubscribe(detail)">退订</el-button>
        </template>
        <span class="muted">订阅后本条的变更会在 5 分钟内推送给你</span>
      </div>
    </el-drawer>

    <el-dialog v-model="tagDlg.visible" title="打分类标签" width="560px" append-to-body>
      <el-form label-width="110px">
        <el-form-item v-for="dim in filterDims" :key="dim.id" :label="dim.dimName">
          <el-select v-model="tagDlg.values[dim.id]" placeholder="不设置" clearable style="width:100%">
            <el-option v-for="v in dim.values" :key="v.id" :label="v.valueName" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-empty v-if="!filterDims.length" description="本部门暂无可用的分类维度" :image-size="60" />
      </el-form>
      <template #footer>
        <el-button @click="tagDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="saveTags">保存</el-button>
      </template>
    </el-dialog>

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
  getLedger, ledgerOptions, searchLedgerOwners,
  downloadLedgerAttachment, fetchLedgerPreview
} from '@/api/ledger'
import {
  categorySelect, assignLedgerCategory, pageRepository,
  itemSubscriptionState, subscribeItem, unsubscribeItem
} from '@/api/subscription'

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

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', type: '', ledgerNoLike: '', ownerId: null,
  deptId: null, status: '', objectType: '', objectKeyword: '',
  uploadStart: '', uploadEnd: '', modifyStart: '', modifyEnd: '' })
const uploadRange = ref([])
const modifyRange = ref([])
const filterDims = ref([])
const categorySel = reactive({})
const detail = reactive({ visible: false, ledgerId: null, ledgerNo: '', name: '', type: '', status: '',
  ownerId: null, deptId: null, occurDate: '', version: '', uploadedAt: '', lastModifiedAt: '', remark: '',
  categories: [], files: [], links: [], subscribed: false, subNo: '' })
const tagDlg = reactive({ visible: false, values: {} })
const previewDlg = reactive({ visible: false, name: '', kind: '', url: '' })
const locatedId = ref(null)

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
const rowClass = ({ row }) => (row.ledgerId === locatedId.value ? 'row-located' : '')

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

async function loadDims() {
  try {
    const res = await categorySelect()
    // 分类筛选只列启用的维度与值; 停用值的历史标签仍在列表/详情里可见(AC-B1-4)
    filterDims.value = (res.data || []).map(d => ({ ...d, values: d.values || [] }))
  } catch { filterDims.value = [] }
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

/**
 * 分类条件按参数名展开为 bracket 键再交给 axios:
 * categoryFilter.dims[0].dimId=5 & categoryFilter.dims[0].valueIds=12 & …=13
 * 后端 LedgerQuery 里 categoryFilter 是对象、dims 是列表, 与参数路径逐段对应。
 */
function buildParams() {
  const p = { ...query }
  let index = 0
  for (const dim of filterDims.value) {
    const valueIds = categorySel[dim.id]
    if (!valueIds || !valueIds.length) continue
    p[`categoryFilter.dims[${index}].dimId`] = dim.id
    p[`categoryFilter.dims[${index}].valueIds`] = valueIds
    index++
  }
  return p
}

async function load() {
  if (!validateRange()) return
  loading.value = true
  try {
    const res = await pageRepository(buildParams())
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

function validateRange() {
  if (query.uploadStart && query.uploadEnd && query.uploadStart > query.uploadEnd) {
    ElMessage.warning('起始时间不能大于结束时间'); return false
  }
  if (query.modifyStart && query.modifyEnd && query.modifyStart > query.modifyEnd) {
    ElMessage.warning('起始时间不能大于结束时间'); return false
  }
  return true
}

function onRangeChange(which) {
  const r = which === 'upload' ? uploadRange.value : modifyRange.value
  query[which + 'Start'] = r?.[0] || ''
  query[which + 'End'] = r?.[1] || ''
}

function search() { query.pageNum = 1; load() }

function clearCategory() {
  Object.keys(categorySel).forEach(k => { categorySel[k] = [] })
  search()
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, keyword: '', type: '', ledgerNoLike: '', ownerId: null, deptId: null,
    status: '', objectType: '', objectKeyword: '',
    uploadStart: '', uploadEnd: '', modifyStart: '', modifyEnd: '' })
  uploadRange.value = []
  modifyRange.value = []
  clearCategory()
}

async function onExpand(row, expanded) {
  const isOpen = Array.isArray(expanded) ? expanded.some(r => r.ledgerId === row.ledgerId) : !!expanded
  if (!isOpen || attachments[row.ledgerId]) return
  attLoading[row.ledgerId] = true
  try {
    const res = await getLedger(row.ledgerId)
    attachments[row.ledgerId] = res.data.attachments || []
  } finally { attLoading[row.ledgerId] = false }
}

async function openDetail(row) {
  Object.assign(detail, {
    visible: true,
    ledgerId: row.ledgerId,
    ledgerNo: row.ledgerNo,
    name: row.name,
    type: row.type,
    status: row.status,
    ownerId: row.ownerId,
    deptId: row.deptId,
    occurDate: row.occurDate,
    version: row.version,
    uploadedAt: row.uploadedAt,
    lastModifiedAt: row.lastModifiedAt,
    remark: row.remark,
    categories: row.categories || [],
    files: row.files || [],
    links: [],
    subscribed: false,
    subNo: ''
  })
  try {
    const res = await getLedger(row.ledgerId)
    detail.links = res.data.links || []
    // 详情里的附件沿用台账口径(含上传人等信息), 覆盖列表带来的精简版
    detail.files = (res.data.attachments || []).map(a => ({
      attachmentId: a.id, fileName: a.fileName, fileSize: a.fileSize, previewable: isPreviewable(a.fileName)
    }))
  } catch { /* 详情补充失败不影响抽屉主体展示 */ }
  refreshSubscribeState(row.ledgerId)
}

async function refreshSubscribeState(ledgerId) {
  try {
    const res = await itemSubscriptionState(ledgerId)
    detail.subscribed = !!res.data?.subscribed
    detail.subNo = res.data?.subNo || ''
  } catch { detail.subscribed = false; detail.subNo = '' }
}

async function toggleSubscribe(row) {
  const ledgerId = row.ledgerId
  if (!ledgerId) return
  try {
    if (row.subscribed) {
      await unsubscribeItem(ledgerId)
      row.subscribed = false
      row.subNo = ''
      ElMessage.success('已退订')
    } else {
      await subscribeItem(ledgerId)
      await refreshSubscribeState(ledgerId)
      row.subscribed = detail.subscribed
      row.subNo = detail.subNo
      ElMessage.success('已订阅，本条的变更会在 5 分钟内推送给你')
    }
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

function isPreviewable(name) {
  const ext = String(name || '').toLowerCase().slice(String(name || '').lastIndexOf('.'))
  return ['.pdf', '.jpg', '.jpeg', '.png'].includes(ext)
}

async function downloadAttachment(row) {
  try { await downloadLedgerAttachment(row.id, row.fileName) } catch { ElMessage.error('下载失败') }
}

async function downloadFile(row) {
  try { await downloadLedgerAttachment(row.attachmentId, row.fileName) } catch { ElMessage.error('下载失败') }
}

async function downloadFirst(row) {
  const file = (row.files || [])[0]
  if (!file) { ElMessage.warning('该资料没有可下载的附件'); return }
  await downloadFile(file)
}

async function previewAttachment(row) {
  if (!isPreviewable(row.fileName)) {
    ElMessage.info('该格式暂不支持在线预览，请下载查看')
    return
  }
  await openPreview(row.id, row.fileName)
}

async function previewFile(row) {
  if (!row.previewable) {
    ElMessage.info('该格式暂不支持在线预览，请下载查看')
    return
  }
  await openPreview(row.attachmentId, row.fileName)
}

async function openPreview(attachmentId, name) {
  try {
    const r = await fetchLedgerPreview(attachmentId)
    const kind = r.type.startsWith('image/') ? 'image' : r.type.includes('pdf') ? 'pdf' : 'other'
    previewDlg.name = name
    previewDlg.kind = kind
    previewDlg.url = r.url
    previewDlg.visible = true
  } catch { ElMessage.error('预览失败，请改用下载查看') }
}

async function openTagDlg() {
  Object.keys(tagDlg.values).forEach(k => delete tagDlg.values[k])
  // 回显: 用现有标签比对维度与值名称, 命中则预选
  for (const dim of filterDims.value) {
    const hit = detail.categories.find(c => c.startsWith(dim.dimName + ':'))
    if (!hit) continue
    const valueName = hit.slice(dim.dimName.length + 1)
    const value = dim.values.find(v => v.valueName === valueName)
    if (value) tagDlg.values[dim.id] = value.id
  }
  tagDlg.visible = true
}

async function saveTags() {
  const items = filterDims.value
    .filter(dim => tagDlg.values[dim.id])
    .map(dim => ({ dimId: dim.id, valueId: tagDlg.values[dim.id] }))
  try {
    await assignLedgerCategory(detail.ledgerId, items)
    tagDlg.visible = false
    ElMessage.success('标签已保存')
    const res = await pageRepository({ ledgerId: detail.ledgerId })
    const fresh = (res.data.records || [])[0]
    if (fresh) {
      const row = tableData.value.find(r => r.ledgerId === detail.ledgerId)
      if (row) row.categories = fresh.categories
      detail.categories = fresh.categories
    }
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  }
}

function jump(row) { if (row.jumpPath) router.push(row.jumpPath) }

/** 从报告/通知链接进入时带 ledgerId: 直接打开该条详情(AC-B6-6 一次点击直达) */
async function locateFromQuery() {
  const ledgerId = Number(route.query.ledgerId)
  if (!ledgerId) return
  try {
    const res = await pageRepository({ ledgerId })
    const row = (res.data.records || [])[0]
    if (!row) {
      ElMessage.warning('该资料不存在，或你没有查看权限')
      return
    }
    locatedId.value = ledgerId
    await openDetail(row)
  } catch {
    ElMessage.warning('该资料不存在，或你没有查看权限')
  }
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadDims(), loadDepts()])
  await loadOwners()
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  if (route.query.type) query.type = String(route.query.type)
  await load()
  await locateFromQuery()
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
.cat-tag { margin: 0 6px 4px 0; }
.muted { color: #909399; font-size: 12px; }
.cat-box { display: flex; align-items: center; flex-wrap: wrap; }
.cat-edit { margin-left: auto; }
.sub-box { display: flex; align-items: center; gap: 12px; }
:deep(.row-located) { background: #fff8e6; }
</style>
