<template>
  <div class="page-container">
    <OperationGuide module-key="ledger" />

    <el-card>
      <!-- 组合筛选：与关系，起止同日可查，起>止拦截（AC-A5-1 ~ A5-5） -->
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
        <el-button v-if="can('ledger:add')" type="primary" icon="Plus" @click="openForm()">新建证据</el-button>
        <el-button v-if="can('ledger:edit')" icon="Link" :disabled="!selectedIds.length" @click="openBatchLink">
          批量关联<span v-if="selectedIds.length">（{{ selectedIds.length }}）</span>
        </el-button>
        <span class="tb-tip">单条证据附件上限 {{ limits.maxAttachments }} 个 / 单文件 {{ limits.maxFileSizeMb }}MB</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="onSelectionChange">
        <el-table-column v-if="can('ledger:edit')" type="selection" width="46" />
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
        <el-table-column label="最后修改" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.lastModifiedBy || '—' }}
            <div class="cell-sub">{{ fmtTime(row.lastModifiedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="can('ledger:edit')" link type="warning" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="can('ledger:delete')" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- ===== 统一录入：信息 → 附件 → 关联 → 提交，全过程零页面跳转（AC-A1-1/A1-2） ===== -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑证据' : '新建证据'" width="880px" top="5vh"
      :close-on-click-modal="false">
      <el-form :model="form.data" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="证据编号">
              <el-input :model-value="form.id ? form.data.ledgerNo : '提交后由系统生成'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上传时间">
              <el-input :model-value="form.id ? fmtTime(form.data.uploadedAt) : '提交后由系统生成'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证据名称" :required="!isDraft">
              <el-input v-model="form.data.name" :maxlength="limits.nameMax" show-word-limit
                placeholder="不超过 100 字" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证据类型" :required="!isDraft">
              <el-select v-model="form.data.type" placeholder="请选择" clearable style="width:100%">
                <el-option v-for="t in options.types" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人" :required="!isDraft">
              <el-select v-model="form.data.ownerId" placeholder="请选择" clearable filterable remote
                :remote-method="loadOwners" :loading="ownerLoading" style="width:100%">
                <el-option v-for="o in owners" :key="o.id" :label="o.realName || o.username" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生时间" :required="!isDraft">
              <el-date-picker v-model="form.data.occurDate" type="date" value-format="YYYY-MM-DD"
                :disabled-date="afterToday" placeholder="不得晚于当前日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源部门" :required="!isDraft">
              <el-select v-model="form.data.deptId" placeholder="默认取当前部门" clearable filterable style="width:100%">
                <el-option v-for="d in deptFlat" :key="d.id" :label="d.label" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.data.version" :maxlength="limits.versionMax" placeholder="不超过 20 字" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.data.status">
                <el-radio label="SUBMITTED">已提交</el-radio>
                <el-radio label="DRAFT">草稿</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.data.remark" :maxlength="limits.remarkMax" show-word-limit placeholder="不超过 500 字" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 附件 -->
        <el-divider content-position="left">附件资料（{{ form.files.length }} / {{ limits.maxAttachments }}）</el-divider>
        <div class="file-bar">
          <input ref="fileInput" type="file" multiple style="display:none" @change="onPickFiles" />
          <el-button icon="Upload" @click="fileInput.click()">选择文件</el-button>
          <span class="file-tip">允许 {{ limits.allowExts.join(' / ') }}，单文件 ≤ {{ limits.maxFileSizeMb }}MB</span>
        </div>
        <el-table :data="form.files" size="small" border style="margin-top:8px">
          <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ humanSize(row.size) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="form.files.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="form.id" class="file-tip">编辑时新增的附件将在保存后追加到本条证据。</div>

        <!-- 关联对象 -->
        <el-divider content-position="left">关联业务对象（{{ form.links.length }} / {{ limits.maxLinks }}）</el-divider>
        <div class="link-bar">
          <el-select v-model="linkPick.objectType" placeholder="对象类型" style="width:130px">
            <el-option v-for="o in options.objectTypes" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="linkPick.keyword" placeholder="输入编号或名称检索" style="width:220px"
            @keyup.enter="searchObjects" />
          <el-button icon="Search" @click="searchObjects">检索</el-button>
        </div>
        <el-table :data="linkPick.candidates" size="small" border style="margin-top:8px" max-height="180">
          <el-table-column prop="objectNo" label="编号" width="150" />
          <el-table-column prop="objectName" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="addLink(row)">关联</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-table :data="form.links" size="small" border style="margin-top:8px">
          <el-table-column label="已关联" min-width="280">
            <template #default="{ row }">{{ row.objectType }} · {{ row.objectNo || row.objectId }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="form.links.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button :loading="submitting" @click="submit('DRAFT')">存为草稿</el-button>
        <el-button type="primary" :loading="submitting" @click="submit('SUBMITTED')">提交</el-button>
      </template>
    </el-dialog>

    <!-- ===== 详情：附件 + 关联清单 + 变更轨迹 ===== -->
    <el-drawer v-model="detail.visible" :title="'证据详情 · ' + (detail.ledger?.ledgerNo || '')" size="760px">
      <el-descriptions v-if="detail.ledger" :column="2" border size="small">
        <el-descriptions-item label="证据名称" :span="2">{{ detail.ledger.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="证据类型">{{ typeLabel(detail.ledger.type) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.ledger.status) }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ ownerLabel(detail.ledger.ownerId) }}</el-descriptions-item>
        <el-descriptions-item label="来源部门">{{ deptLabel(detail.ledger.deptId) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ detail.ledger.occurDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.ledger.version || '—' }}</el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ fmtTime(detail.ledger.uploadedAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后修改">
          {{ detail.ledger.lastModifiedBy || '—' }} · {{ fmtTime(detail.ledger.lastModifiedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.ledger.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="detail.tab">
        <el-tab-pane label="附件资料" name="att">
          <div v-if="can('ledger:edit')" class="file-bar">
            <input ref="attachInput" type="file" multiple style="display:none" @change="onAppendFiles" />
            <el-button size="small" icon="Upload" @click="attachInput.click()">追加附件</el-button>
          </div>
          <el-table :data="detail.attachments" size="small" border>
            <el-table-column prop="fileName" label="文件名" min-width="190" show-overflow-tooltip />
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="previewAttachment(row)">预览</el-button>
                <el-button link type="primary" size="small" @click="downloadAttachment(row)">下载</el-button>
                <el-button v-if="can('ledger:edit')" link type="danger" size="small"
                  @click="removeAttachment(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!detail.attachments.length" description="暂无附件" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="关联对象" name="link">
          <el-table :data="detail.links" size="small" border>
            <el-table-column label="对象类型" width="100">
              <template #default="{ row }">{{ objectTypeLabel(row.objectType) }}</template>
            </el-table-column>
            <el-table-column prop="objectNo" label="编号" width="140" />
            <el-table-column prop="objectName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.linkStatus === 'INVALID'" type="danger" size="small">
                  失效关联({{ row.invalidReason === 'OBSOLETE' ? '已作废' : '已删除' }})
                </el-tag>
                <el-tag v-else type="success" size="small">有效</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="jump(row)">前往</el-button>
                <el-button v-if="can('ledger:edit')" link type="danger" size="small"
                  @click="unlink(row)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="file-tip">解除关联只解除引用，证据记录本身保留。</div>
        </el-tab-pane>

        <el-tab-pane label="变更轨迹" name="trail">
          <el-timeline v-if="trail.history.length || trail.logs.length">
            <el-timeline-item v-for="(h, i) in trail.history" :key="'h' + i" :timestamp="fmtTime(h.changedAt)" placement="top">
              <b>{{ h.changeType }}</b> · v{{ h.version }} by {{ h.changedBy }}
              <div v-if="h.changes" class="cell-sub">{{ summary(h.changes) }}</div>
            </el-timeline-item>
            <el-timeline-item v-for="(l, i) in trail.logs" :key="'l' + i" :timestamp="fmtTime(l.createdAt)"
              placement="top" :type="l.result === 1 ? 'success' : 'danger'">
              {{ l.operation }} by {{ l.operator || l.username }} · IP {{ l.ip || '—' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无轨迹" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- ===== 批量关联（AC-A5-7/A5-8） ===== -->
    <el-dialog v-model="batch.visible" title="批量关联" width="640px">
      <el-alert type="info" :closable="false" show-icon
        :title="`已选 ${selectedIds.length} 条证据，单次批量关联上限 ${limits.batchLinkMax} 条`" style="margin-bottom:12px" />
      <div class="link-bar">
        <el-select v-model="linkPick.objectType" placeholder="对象类型" style="width:130px">
          <el-option v-for="o in options.objectTypes" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="linkPick.keyword" placeholder="输入编号或名称检索" style="width:220px"
          @keyup.enter="searchObjects" />
        <el-button icon="Search" @click="searchObjects">检索</el-button>
      </div>
      <el-table :data="linkPick.candidates" size="small" border style="margin-top:8px" max-height="240">
        <el-table-column prop="objectNo" label="编号" width="150" />
        <el-table-column prop="objectName" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="runBatchLink(row)">关联</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="batch.visible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="batchResult.visible" title="批量操作结果" width="560px">
      <el-result :icon="batchResult.failCount ? 'warning' : 'success'"
        :title="`成功 ${batchResult.successCount} 条，失败 ${batchResult.failCount} 条`" />
      <el-table v-if="batchResult.failures.length" :data="batchResult.failures" size="small" border>
        <el-table-column prop="target" label="对象" width="150" />
        <el-table-column prop="reason" label="失败原因" min-width="240" show-overflow-tooltip />
      </el-table>
      <template #footer><el-button type="primary" @click="batchResult.visible = false">知道了</el-button></template>
    </el-dialog>

    <el-dialog v-model="previewDlg.visible" :title="previewDlg.name" width="860px" top="5vh" append-to-body>
      <img v-if="previewDlg.kind === 'image'" :src="previewDlg.url" class="lg-img" />
      <iframe v-else-if="previewDlg.kind === 'pdf'" :src="previewDlg.url" class="lg-frame" />
      <el-empty v-else description="该格式暂不支持在线预览，请下载查看" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getDepartmentTree } from '@/api/system'
import {
  pageLedger, getLedger, createLedger, updateLedger, deleteLedger, batchUploadLedger,
  ledgerOptions, searchLedgerObjects, searchLedgerOwners, ledgerTrail,
  removeLedgerLink, removeLedgerAttachment, appendLedgerAttachments, uploadLedgerFile,
  downloadLedgerAttachment, fetchLedgerPreview, batchLinkLedger
} from '@/api/ledger'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const can = (code) => userStore.hasPermission(code)

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedIds = ref([])
const owners = ref([])
const ownerLoading = ref(false)
const depts = ref([])
const fileInput = ref(null)
const attachInput = ref(null)

const options = reactive({ types: [], statuses: [], objectTypes: [], supportedObjectTypes: [] })
const limits = reactive({ maxAttachments: 20, maxFileSizeMb: 200, allowExts: [], maxLinks: 20, batchLinkMax: 100,
  nameMax: 100, remarkMax: 500, versionMax: 20, pageSizes: [20, 50, 100] })
const pageSizes = computed(() => limits.pageSizes?.length ? limits.pageSizes : [20, 50, 100])

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', type: '', ownerId: null, deptId: null,
  status: '', objectType: '', objectKeyword: '', occurStart: '', occurEnd: '', uploadStart: '', uploadEnd: '' })
const occurRange = ref([])
const uploadRange = ref([])

const form = reactive({ visible: false, id: null, data: {}, files: [], originalFileIds: [] })
const linkPick = reactive({ objectType: 'PROJECT', keyword: '', candidates: [] })
const detail = reactive({ visible: false, ledger: null, attachments: [], links: [], tab: 'att' })
const trail = reactive({ history: [], logs: [] })
const batch = reactive({ visible: false })
const batchResult = reactive({ visible: false, successCount: 0, failCount: 0, failures: [] })
const previewDlg = reactive({ visible: false, name: '', kind: '', url: '' })

const isDraft = computed(() => form.data.status === 'DRAFT')
const today = new Date().toISOString().slice(0, 10)

const typeLabel = (v) => options.types.find(t => t.value === v)?.label || v || '—'
const statusLabel = (s) => ({ DRAFT: '草稿', SUBMITTED: '已提交', ARCHIVED: '已归档', VOID: '作废' }[s] || s || '—')
const statusTag = (s) => ({ DRAFT: 'info', SUBMITTED: 'success', ARCHIVED: 'warning', VOID: 'danger' }[s] || 'info')
const objectTypeLabel = (v) => options.objectTypes.find(o => o.value === v)?.label || v
const ownerLabel = (id) => owners.value.find(o => o.id === id)?.realName || (id ? '#' + id : '—')
const deptLabel = (id) => deptFlat.value.find(d => d.id === id)?.label || (id ? '#' + id : '—')
const typeLabels = (v) => options.types.find(t => t.value === v)?.label || v
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')
const humanSize = (n) => {
  if (n == null) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
const afterToday = (d) => d.getTime() > Date.now()
const summary = (changes) => {
  const rows = Array.isArray(changes) ? changes : Object.entries(changes || {}).map(([k, v]) => ({ field: k, after: v }))
  return rows.slice(0, 6).map(r => `${r.field}: ${r.before ?? '—'} → ${r.after ?? '—'}`).join('；') || '—'
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

// ---------- 初始化 ----------

async function loadOptions() {
  const res = await ledgerOptions()
  const d = res.data || {}
  options.types = d.types || []
  options.statuses = d.statuses || []
  options.objectTypes = d.objectTypes || []
  options.supportedObjectTypes = d.supportedObjectTypes || []
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

// ---------- 列表 ----------

async function load() {
  if (!validateRange()) return
  loading.value = true
  try {
    const res = await pageLedger(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

/** AC-A5-3：起 > 止 拦截并提示 */
function validateRange() {
  if (query.occurStart && query.occurEnd && query.occurStart > query.occurEnd) {
    ElMessage.warning('起始时间不能大于结束时间')
    return false
  }
  if (query.uploadStart && query.uploadEnd && query.uploadStart > query.uploadEnd) {
    ElMessage.warning('起始时间不能大于结束时间')
    return false
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

function onSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

// ---------- 录入（单界面内完成，AC-A1/A2） ----------

async function openForm(row) {
  if (!owners.value.length) await loadOwners()
  Object.keys(form.data).forEach(k => delete form.data[k])
  form.files = []
  form.originalFileIds = []
  linkPick.keyword = ''
  linkPick.candidates = []
  if (row) {
    const res = await getLedger(row.id)
    const d = res.data
    form.id = d.ledger.id
    form.data = { ...d.ledger }
    form.originalFileIds = (d.attachments || []).map(a => a.fileId)
    form.links = (d.links || []).map(l => ({ objectType: l.objectType, objectId: l.objectId,
      objectNo: l.objectNo, objectName: l.objectName }))
    form.existingAttachments = d.attachments || []
  } else {
    form.id = null
    form.data = { status: 'SUBMITTED', occurDate: today, ownerId: userStore.userId || null }
    form.links = []
    form.existingAttachments = []
    // 从模块侧「录入证据」入口带入的预置关联（AC-A1-1 入口）
    if (route.query.objectType && route.query.objectId) {
      form.links.push({ objectType: String(route.query.objectType), objectId: String(route.query.objectId),
        objectNo: String(route.query.objectId) })
    }
  }
  form.visible = true
}

/** 客户端先于提交校验：格式/大小/数量（与服务端同口径，AC-A1-4/5/6） */
function onPickFiles(e) {
  const picked = Array.from(e.target.files || [])
  e.target.value = ''
  const err = acceptFiles(picked)
  if (err) ElMessage.error(err)
}

function acceptFiles(picked) {
  for (const f of picked) {
    const ext = '.' + (f.name.includes('.') ? f.name.split('.').pop().toLowerCase() : '')
    if (!limits.allowExts.includes(ext)) {
      return `不支持的附件格式：${ext || f.name}；允许的格式：${limits.allowExts.join(' / ')}`
    }
    if (f.size > limits.maxFileSizeMb * 1024 * 1024) {
      return `附件「${f.name}」为 ${humanSize(f.size)}，超过单文件上限 ${limits.maxFileSizeMb}MB`
    }
  }
  if (form.files.length + picked.length > limits.maxAttachments) {
    return `单条证据附件最多 ${limits.maxAttachments} 个`
  }
  form.files = form.files.concat(picked)
  return null
}

async function searchObjects() {
  if (!linkPick.objectType) { ElMessage.warning('请先选择对象类型'); return }
  const res = await searchLedgerObjects(linkPick.objectType, linkPick.keyword, 20)
  linkPick.candidates = res.data || []
}

function addLink(row) {
  if (form.links.length >= limits.maxLinks) {
    ElMessage.warning(`单条证据关联对象最多 ${limits.maxLinks} 个`)
    return
  }
  if (form.links.some(l => l.objectType === linkPick.objectType && String(l.objectId) === String(row.objectId))) {
    ElMessage.warning('该对象已关联')
    return
  }
  form.links.push({ objectType: linkPick.objectType, objectId: row.objectId,
    objectNo: row.objectNo, objectName: row.objectName })
}

/** 提交/存草稿前的完整校验；草稿只做长度与格式校验（AC-A2-7） */
function validateForm(status) {
  const draft = status === 'DRAFT'
  const d = form.data
  if (d.name && d.name.length > limits.nameMax) return `证据名称不得超过 ${limits.nameMax} 字`
  if (d.remark && d.remark.length > limits.remarkMax) return `备注不得超过 ${limits.remarkMax} 字`
  if (d.version && d.version.length > limits.versionMax) return `版本不得超过 ${limits.versionMax} 字`
  if (d.occurDate && d.occurDate > today) return '发生时间不得晚于当前日期'
  if (form.files.length > limits.maxAttachments) return `单条证据附件最多 ${limits.maxAttachments} 个`
  if (form.links.length > limits.maxLinks) return `单条证据关联对象最多 ${limits.maxLinks} 个`
  if (draft) return null
  const missing = []
  if (!d.name || !d.name.trim()) missing.push('证据名称')
  if (!d.type) missing.push('证据类型')
  if (!d.ownerId) missing.push('责任人')
  if (!d.occurDate) missing.push('发生时间')
  if (!d.deptId) missing.push('来源部门')
  if (!form.id && !form.files.length) missing.push('附件')
  if (!form.links.length) missing.push('关联对象')
  if (missing.length) return '请补全必填项：' + missing.join('、')
  return null
}

function metaOf(status) {
  const d = form.data
  return {
    name: d.name, type: d.type, ownerId: d.ownerId, occurDate: d.occurDate,
    version: d.version, deptId: d.deptId, remark: d.remark, status,
    links: form.links.map(l => ({ objectType: l.objectType, objectId: String(l.objectId) }))
  }
}

async function submit(status) {
  const err = validateForm(status)
  if (err) { ElMessage.error(err); return }
  submitting.value = true
  try {
    if (form.id) {
      await updateLedger(form.id, metaOf(status))
      if (form.files.length) await appendFiles(form.id, form.files)
      ElMessage.success('保存成功')
    } else if (form.files.length) {
      // N 个文件合并为 1 条多附件证据（AC-A5-6）
      await batchUploadLedger(metaOf(status), form.files)
      ElMessage.success('提交成功')
    } else {
      await createLedger(metaOf(status))
      ElMessage.success(status === 'DRAFT' ? '草稿已保存' : '提交成功')
    }
    form.visible = false
    if (route.query.objectType) router.replace({ path: '/ledger/list' })
    await load()
  } finally { submitting.value = false }
}

async function appendFiles(id, files) {
  const ids = []
  for (const f of files) {
    const res = await uploadLedgerFile(f)
    ids.push(res.data.id ?? res.data)
  }
  await appendLedgerAttachments(id, ids)
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除证据 [${row.ledgerNo}]？删除后不可恢复。`, '警告', { type: 'warning' })
  await deleteLedger(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------- 详情 ----------

async function openDetail(row) {
  const res = await getLedger(row.id)
  detail.ledger = res.data.ledger
  detail.attachments = res.data.attachments || []
  detail.links = res.data.links || []
  detail.tab = 'att'
  detail.visible = true
  const t = await ledgerTrail(row.ledgerNo)
  trail.history = t.data.history || []
  trail.logs = t.data.logs || []
}

async function onAppendFiles(e) {
  const picked = Array.from(e.target.files || [])
  e.target.value = ''
  const err = acceptFiles(picked)
  if (err) { ElMessage.error(err); return }
  const staged = form.files
  form.files = []
  try {
    await appendFiles(detail.ledger.id, picked)
    ElMessage.success('附件已追加')
    await openDetail({ id: detail.ledger.id, ledgerNo: detail.ledger.ledgerNo })
    load()
  } finally { form.files = staged }
}

async function removeAttachment(row) {
  await ElMessageBox.confirm(`移除附件「${row.fileName}」？`, '提示', { type: 'warning' })
  await removeLedgerAttachment(detail.ledger.id, row.id)
  ElMessage.success('已移除')
  await openDetail({ id: detail.ledger.id, ledgerNo: detail.ledger.ledgerNo })
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

async function unlink(row) {
  await ElMessageBox.confirm('解除关联只解除引用，证据记录本身保留。是否继续？', '提示', { type: 'warning' })
  await removeLedgerLink(detail.ledger.id, row.id)
  ElMessage.success('已解除关联')
  await openDetail({ id: detail.ledger.id, ledgerNo: detail.ledger.ledgerNo })
}

function jump(row) {
  if (row.jumpPath) router.push(row.jumpPath)
}

// ---------- 批量关联（AC-A5-7/A5-8） ----------

function openBatchLink() {
  if (selectedIds.value.length > limits.batchLinkMax) {
    ElMessage.warning(`单次批量关联最多 ${limits.batchLinkMax} 条，当前 ${selectedIds.value.length} 条`)
    return
  }
  linkPick.keyword = ''
  linkPick.candidates = []
  batch.visible = true
}

async function runBatchLink(row) {
  const res = await batchLinkLedger(selectedIds.value, linkPick.objectType, String(row.objectId))
  const d = res.data
  batchResult.successCount = d.successCount
  batchResult.failCount = d.failCount
  batchResult.failures = d.failures || []
  batchResult.visible = true
  batch.visible = false
  load()
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadDepts()])
  await loadOwners()
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  await load()
  // 模块侧「录入证据」直达：进入即打开录入表单，录入仍在台账单一界面内（AC-A1-1/A1-2）
  if (route.query.objectType && can('ledger:add')) {
    await nextTick()
    openForm()
  }
})
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 10px;
  .tb-tip { margin-left: auto; color: #909399; font-size: 12px; }
}
.file-bar { display: flex; align-items: center; gap: 10px;
  .file-tip { color: #909399; font-size: 12px; }
}
.link-bar { display: flex; align-items: center; gap: 8px; }
.cell-sub { color: #909399; font-size: 12px; line-height: 1.4; }
.file-tip { color: #909399; font-size: 12px; margin-top: 6px; }
.lg-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.lg-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
</style>
