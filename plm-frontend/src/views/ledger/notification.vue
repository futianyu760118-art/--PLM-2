<template>
  <div class="page-container">
    <OperationGuide module-key="ledgerNotify" />

    <el-card>
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane :label="`我的通知${unread ? ' (' + unread + ')' : ''}`" name="list">
          <el-form :inline="true">
            <el-form-item label="状态">
              <el-select v-model="query.readStatus" placeholder="全部" clearable style="width:120px" @change="search">
                <el-option label="未读" :value="0" />
                <el-option label="已读" :value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="通知类型">
              <el-select v-model="query.notifyType" placeholder="全部" clearable style="width:170px" @change="search">
                <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button icon="Refresh" @click="load">刷新</el-button>
              <el-button type="primary" plain :disabled="!unread" @click="readAll">全部已读</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="rows" border @row-click="open" style="cursor:pointer">
            <el-table-column width="60" align="center">
              <template #default="{ row }">
                <el-badge v-if="row.readStatus === 0" is-dot type="danger" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag(row.notifyType)">{{ typeLabel(row.notifyType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
            <el-table-column label="对象" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.objectType }} · {{ row.objectId }}</template>
            </el-table-column>
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !rows.length" description="暂无通知" :image-size="70" />

          <div class="pagination-wrap">
            <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total"
              :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @size-change="load"
              @current-change="load" />
          </div>
        </el-tab-pane>

        <!-- 按类型开关（AC-A4-11） -->
        <el-tab-pane label="通知设置" name="settings">
          <el-alert type="info" :closable="false" show-icon
            title="关闭某类通知后，该类变更将不再推送给你；已产生的历史通知不受影响。" style="margin-bottom:12px" />
          <el-table :data="settings" border>
            <el-table-column label="通知类型" min-width="200">
              <template #default="{ row }">{{ typeLabel(row.notifyType) }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ typeDesc(row.notifyType) }}</template>
            </el-table-column>
            <el-table-column label="接收" width="120" align="center">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled === 1" @change="v => toggle(row, v)" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 通知详情：含变更字段的前后值与操作人、时间、跳转链接（AC-A4-8） -->
    <el-drawer v-model="detail.visible" :title="detail.notification?.title || '通知详情'" size="640px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="类型">{{ typeLabel(detail.notification?.notifyType) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.content?.operator || '—' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ fmtTime(detail.content?.time || detail.notification?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="对象">
          {{ detail.notification?.objectType }} · {{ detail.notification?.objectId }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="detail.content?.link" class="mt12">
        <el-button type="primary" link icon="Link" @click="goLink">前往查看</el-button>
      </div>

      <el-divider content-position="left">变更明细（{{ changes.length }} 条）</el-divider>
      <el-table :data="changes" size="small" border>
        <el-table-column prop="field" label="字段" width="140" />
        <el-table-column prop="before" label="变更前" min-width="140" show-overflow-tooltip />
        <el-table-column prop="after" label="变更后" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
      </el-table>
      <el-empty v-if="!changes.length" description="无字段级变更" :image-size="60" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  pageLedgerNotification, ledgerUnreadCount, getLedgerNotification,
  readLedgerNotification, readAllLedgerNotification, ledgerNotifySettings, saveLedgerNotifySetting
} from '@/api/ledger'

const TYPES = [
  { value: 'LEDGER_CREATED', label: '证据新建', desc: '有人新增了与你相关的证据记录' },
  { value: 'LEDGER_CHANGED', label: '证据变更', desc: '证据字段被修改（含前后值）' },
  { value: 'LEDGER_DELETED', label: '证据删除', desc: '证据记录被删除' },
  { value: 'LEDGER_LINK', label: '关联变更', desc: '证据与业务对象的关联新增或解除' },
  { value: 'OBJ_INVALID', label: '关联失效', desc: '被关联对象已删除或已作废' }
]

const router = useRouter()
const tab = ref('list')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const unread = ref(0)
const settings = ref([])
const query = reactive({ readStatus: null, notifyType: '', page: 1, pageSize: 20 })
const detail = reactive({ visible: false, notification: null, content: null })

const changes = computed(() => {
  const c = detail.content?.changes
  return Array.isArray(c) ? c : []
})

const typeLabel = (v) => TYPES.find(t => t.value === v)?.label || v || '—'
const typeDesc = (v) => TYPES.find(t => t.value === v)?.desc || '—'
const typeTag = (v) => ({ LEDGER_CREATED: 'success', LEDGER_CHANGED: 'warning', LEDGER_DELETED: 'danger',
  LEDGER_LINK: 'primary', OBJ_INVALID: 'info' }[v] || 'info')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')

async function load() {
  loading.value = true
  try {
    const res = await pageLedgerNotification(query)
    rows.value = res.data.records || []
    total.value = res.data.total || 0
    const u = await ledgerUnreadCount()
    unread.value = u.data.count || 0
  } finally { loading.value = false }
}

function search() { query.page = 1; load() }

async function open(row) {
  const res = await getLedgerNotification(row.id)
  detail.notification = res.data.notification
  detail.content = res.data.content
  detail.visible = true
  if (row.readStatus === 0) {
    await readLedgerNotification([row.id])
    row.readStatus = 1
    await refreshUnread()
  }
}

async function refreshUnread() {
  const u = await ledgerUnreadCount()
  unread.value = u.data.count || 0
}

async function readAll() {
  await readAllLedgerNotification()
  ElMessage.success('已全部标记为已读')
  load()
}

function goLink() {
  const link = detail.content?.link
  if (!link) return
  detail.visible = false
  router.push(link)
}

async function loadSettings() {
  const res = await ledgerNotifySettings()
  settings.value = res.data || []
}

async function toggle(row, v) {
  await saveLedgerNotifySetting(row.notifyType, v)
  row.enabled = v ? 1 : 0
  ElMessage.success(v ? '已开启该类通知' : '已关闭该类通知')
}

function onTabChange(name) {
  if (name === 'settings') loadSettings()
}

onMounted(load)
</script>

<style scoped lang="scss">
.mt12 { margin-top: 12px; }
</style>
