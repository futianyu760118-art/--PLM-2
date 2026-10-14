<template>
  <!-- 模块/表单侧「证据区」：与证据台账读同一行数据，不落副本（AC-A3-1/A3-3） -->
  <div class="ledger-panel">
    <div class="lp-head">
      <span class="lp-title">
        <el-icon><Document /></el-icon>{{ title }}
        <el-tag v-if="rows.length" size="small" effect="plain">{{ rows.length }} 条</el-tag>
      </span>
      <span class="lp-actions">
        <el-button link type="primary" size="small" :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button v-if="writable" link type="primary" size="small" @click="goCreate">
          <el-icon><Plus /></el-icon>录入证据
        </el-button>
      </span>
    </div>

    <el-table v-loading="loading" :data="rows" size="small" border>
      <el-table-column prop="ledgerNo" label="证据编号" width="170">
        <template #default="{ row }">
          <el-link type="primary" @click="open(row)">{{ row.ledgerNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="证据名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ row.typeLabel || row.type || '—' }}</template>
      </el-table-column>
      <el-table-column label="责任人" width="90">
        <template #default="{ row }">{{ row.ownerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="发生时间" width="110">
        <template #default="{ row }">{{ fmtDate(row.occurDate) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="附件" width="80" align="center">
        <template #default="{ row }">
          <el-link v-if="row.attachmentCount" type="primary" @click="open(row)">
            资料 {{ row.attachmentCount }}
          </el-link>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无关联证据" :image-size="60" />

    <!-- 证据详情：附件下载入口与关联清单，与台账页同源 -->
    <el-drawer v-model="detail.visible" :title="'证据详情 · ' + (detail.ledger?.ledgerNo || '')" size="620px">
      <el-descriptions v-if="detail.ledger" :column="1" border size="small">
        <el-descriptions-item label="证据名称">{{ detail.ledger.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="证据类型">{{ detail.ledger.type || '—' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detail.ledger.ownerId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ fmtDate(detail.ledger.occurDate) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.ledger.version || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.ledger.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="最后修改">
          {{ detail.ledger.lastModifiedBy || '—' }} · {{ fmtTime(detail.ledger.lastModifiedAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider>附件资料</el-divider>
      <el-table :data="detail.attachments" size="small" border>
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="preview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!detail.attachments.length" description="暂无附件" :image-size="60" />

      <el-divider>关联对象</el-divider>
      <el-table :data="detail.links" size="small" border>
        <el-table-column prop="objectType" label="类型" width="90" />
        <el-table-column prop="objectNo" label="编号" width="130" />
        <el-table-column prop="objectName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.linkStatus === 'INVALID'" type="danger" size="small">
              失效({{ row.invalidReason === 'OBSOLETE' ? '已作废' : '已删除' }})
            </el-tag>
            <el-tag v-else type="success" size="small">有效</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="previewDlg.visible" :title="previewDlg.name" width="820px" top="5vh" append-to-body>
        <img v-if="previewDlg.kind === 'image'" :src="previewDlg.url" class="lp-img" />
        <iframe v-else-if="previewDlg.kind === 'pdf'" :src="previewDlg.url" class="lp-frame" />
        <el-empty v-else description="该格式暂不支持在线预览，请下载查看" :image-size="80" />
      </el-dialog>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { ledgerByObject, getLedger, downloadLedgerAttachment, fetchLedgerPreview } from '@/api/ledger'

const props = defineProps({
  objectType: { type: String, required: true },
  objectId: { type: [String, Number], default: '' },
  title: { type: String, default: '关联证据资料' }
})

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const rows = ref([])
const detail = ref({ visible: false, ledger: null, attachments: [], links: [] })
const previewDlg = ref({ visible: false, name: '', kind: '', url: '' })

// 查看人无 ledger:add → 不出现「录入证据」入口（AC-A6-1）
const writable = computed(() => userStore.hasPermission('ledger:add'))

const statusLabel = (s) => ({ DRAFT: '草稿', SUBMITTED: '已提交', ARCHIVED: '已归档', VOID: '作废' }[s] || s || '—')
const statusTag = (s) => ({ DRAFT: 'info', SUBMITTED: 'success', ARCHIVED: 'warning', VOID: 'danger' }[s] || 'info')
const fmtDate = (d) => (d ? String(d).slice(0, 10) : '—')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')
const humanSize = (n) => {
  if (n == null) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

async function load() {
  if (!props.objectId) { rows.value = []; return }
  loading.value = true
  try {
    const res = await ledgerByObject(props.objectType, props.objectId)
    rows.value = res.data || []
  } catch {
    // 无台账查看权限的模块使用者：证据区静默留空，不阻断模块主流程
    rows.value = []
  } finally { loading.value = false }
}

async function open(row) {
  const res = await getLedger(row.id)
  detail.value = {
    visible: true,
    ledger: res.data.ledger,
    attachments: res.data.attachments || [],
    links: res.data.links || []
  }
}

async function preview(row) {
  try {
    const r = await fetchLedgerPreview(row.id)
    const kind = r.type.startsWith('image/') ? 'image'
      : r.type.includes('pdf') ? 'pdf' : 'other'
    previewDlg.value = { visible: true, name: row.fileName, kind, url: r.url }
  } catch {
    ElMessage.error('预览失败，请改用下载查看')
  }
}

async function download(row) {
  try {
    await downloadLedgerAttachment(row.id, row.fileName)
  } catch {
    ElMessage.error('下载失败')
  }
}

function goCreate() {
  // 带到台账页并预置关联对象，录入仍在台账单一界面内完成（AC-A1-1）
  router.push({ path: '/ledger/list', query: { objectType: props.objectType, objectId: String(props.objectId) } })
}

onMounted(load)
watch(() => props.objectId, load)
</script>

<style scoped lang="scss">
.ledger-panel { margin-top: 14px; }
.lp-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 0 10px;
  .lp-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: #303133; }
}
.lp-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.lp-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
</style>
