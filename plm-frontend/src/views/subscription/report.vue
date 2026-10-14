<template>
  <div class="page-container">
    <OperationGuide module-key="subscriptionReport" />

    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的报告</span>
          <el-radio-group v-model="query.type" size="small" @change="search">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="ITEM">单条</el-radio-button>
            <el-radio-button value="PERIOD">周期</el-radio-button>
          </el-radio-group>
          <span class="head-tip">只保留最近 12 期，更早的自动清理</span>
          <el-button icon="Refresh" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="reportNo" label="报告编号" width="180" />
        <el-table-column prop="subName" label="订阅名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.subName || '—' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'ITEM' ? 'info' : 'primary'" effect="plain">
              {{ row.type === 'ITEM' ? '单条' : '周期' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="覆盖周期" min-width="230">
          <template #default="{ row }">{{ periodText(row) }}</template>
        </el-table-column>
        <el-table-column label="资料条数" width="100" align="center">
          <template #default="{ row }">{{ row.itemCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.generatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" size="small" @click="openHtml(row)">正文</el-button>
            <el-button link type="primary" size="small" :disabled="!row.hasExcel"
              @click="download(row)">下载 Excel</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有报告；订阅命中后会自动生成" :image-size="70" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @size-change="load"
          @current-change="load" />
      </div>
    </el-card>

    <el-drawer v-model="detail.visible" :title="'报告详情 · ' + (detail.report.reportNo || '')" size="820px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="订阅名称" :span="2">{{ detail.report.subName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="报告类型">
          {{ detail.report.type === 'ITEM' ? '单条订阅报告' : '周期订阅报告' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.report.status) }}</el-descriptions-item>
        <el-descriptions-item label="覆盖周期" :span="2">{{ periodText(detail.report) }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ fmtTime(detail.report.generatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="推送时间">{{ fmtTime(detail.report.pushedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.report.error" label="失败原因" :span="2">
          <span class="err">{{ detail.report.error }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">
        报告明细（{{ detail.items.length }} 条）
      </el-divider>
      <div class="muted" style="margin-bottom:6px">
        点资料名称即回到资料汇总处并定位该条记录
      </div>
      <el-table :data="detail.items" size="small" border>
        <el-table-column label="来源证据编号" width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="locate(row)">{{ row.ledgerNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="资料名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="locate(row)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="分类标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="c in row.categories" :key="c" size="small" class="cat-tag" effect="plain">{{ c }}</el-tag>
            <span v-if="!row.categories?.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="变更时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.lastModifiedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="!row.files?.length"
              @click="downloadFile(row)">下载</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="muted">本期无更新。</span>
        </template>
      </el-table>

      <el-divider content-position="left">推送记录</el-divider>
      <el-table :data="detail.deliveries" size="small" border>
        <el-table-column label="渠道" width="110">
          <template #default="{ row }">{{ row.channel === 'EMAIL' ? '邮件' : '站内通知' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="resultTag(row.result)">{{ resultLabel(row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="送达对象" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.target || '—' }}</template>
        </el-table-column>
        <el-table-column prop="error" label="说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.error || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.deliveredAt) }}</template>
        </el-table-column>
        <template #empty>
          <span class="muted">暂无投递记录</span>
        </template>
      </el-table>

      <div class="drawer-foot">
        <el-button type="primary" :disabled="!detail.report.hasExcel" @click="download(detail.report)">
          下载 Excel 明细
        </el-button>
        <el-button @click="openHtml(detail.report)">查看 HTML 正文</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="htmlDlg.visible" title="报告正文" width="720px" append-to-body>
      <!-- 正文由后端按报告快照渲染，与邮件里收到的内容逐字一致 -->
      <div class="html-body" v-html="htmlDlg.body"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OperationGuide from '@/components/OperationGuide.vue'
import { pageReport, getReport, reportHtml, downloadReportExcel } from '@/api/subscription'
import { downloadLedgerAttachment } from '@/api/ledger'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, type: '' })

const detail = reactive({ visible: false, report: {}, items: [], deliveries: [] })
const htmlDlg = reactive({ visible: false, body: '' })

async function load() {
  loading.value = true
  try {
    const r = await pageReport(query)
    tableData.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function fmtTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function periodText(row) {
  if (!row) return '—'
  const s = fmtTime(row.periodStart)
  const e = fmtTime(row.periodEnd)
  if (!row.periodStart && !row.periodEnd) return '—'
  if (row.type === 'ITEM') return s
  return s + ' ~ ' + e
}

function statusLabel(s) {
  return { PENDING: '待生成', GENERATED: '已生成', PUSHED: '已推送', FAILED: '生成失败' }[s] || s || '—'
}

function statusTag(s) {
  return { PENDING: 'info', GENERATED: 'primary', PUSHED: 'success', FAILED: 'danger' }[s] || 'info'
}

function resultLabel(r) {
  return { SUCCESS: '成功', FAILED: '失败', SKIPPED: '已跳过' }[r] || r || '—'
}

function resultTag(r) {
  return { SUCCESS: 'success', FAILED: 'danger', SKIPPED: 'info' }[r] || 'info'
}

async function openDetail(row) {
  const r = await getReport(row.id)
  detail.report = r.data?.report || {}
  detail.items = r.data?.items || []
  detail.deliveries = r.data?.deliveries || []
  detail.visible = true
}

async function openHtml(row) {
  const r = await reportHtml(row.id)
  htmlDlg.body = r.data || ''
  htmlDlg.visible = true
}

async function download(row) {
  await downloadReportExcel(row.id, (row.reportNo || 'report') + '.xlsx')
  ElMessage.success('已开始下载')
}

function downloadFile(row) {
  const f = (row.files || [])[0]
  if (f) downloadLedgerAttachment(f.attachmentId, f.fileName)
}

/** 报告里的标题就是链接：点击回到资料汇总处并定位该条（AC-B5-2/B6-6） */
function locate(row) {
  router.push({ path: '/ledger/repository', query: { ledgerId: row.ledgerId } })
}

onMounted(load)
</script>

<style lang="scss" scoped>
.page-container { padding: 16px; }
.card-head { display: flex; align-items: center; gap: 12px; }
.head-tip { flex: 1; font-size: 12.5px; color: #909399; }
.pagination-wrap { margin-top: 12px; display: flex; justify-content: flex-end; }
.cat-tag { margin-right: 4px; }
.muted { color: #909399; font-size: 12px; }
.err { color: #f56c6c; }
.drawer-foot { margin-top: 16px; display: flex; gap: 8px; }
.html-body {
  max-height: 60vh; overflow: auto; padding: 8px;
  border: 1px solid #ebeef5; border-radius: 6px;
}
</style>
