<template>
  <div class="doc-page">
    <!-- 五区卡片 -->
    <el-row :gutter="12" class="zone-row">
      <el-col v-for="z in zones" :key="z.value" :xs="12" :sm="8" :md="4">
        <div class="zone-card" :class="{ active: query.lifecycleState === z.value }" @click="pickZone(z.value)">
          <div class="zone-name">{{ z.label }}</div>
          <div class="zone-count">{{ stats[z.value] || 0 }}</div>
          <div class="zone-desc">{{ z.desc }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="zone-card locked" :class="{ active: query.locked === true }" @click="pickLocked">
          <div class="zone-name">检出锁定</div>
          <div class="zone-count">{{ stats.LOCKED || 0 }}</div>
          <div class="zone-desc">他人正在修改</div>
        </div>
      </el-col>
    </el-row>

    <!-- 查询条件 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="文档编号 / 标题" clearable style="width: 220px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.docType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in DOC_TYPES" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="料号">
          <el-input v-model="query.partNo" placeholder="料号" clearable style="width: 170px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="项目号">
          <el-input v-model="query.projectNo" placeholder="项目号" clearable style="width: 170px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreate">新建文档</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe size="small" @row-dblclick="openDetail">
        <el-table-column prop="docNo" label="文档编号" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ DOC_TYPES[row.docType] || row.docType }}</template>
        </el-table-column>
        <el-table-column prop="partNo" label="料号" width="170" show-overflow-tooltip />
        <el-table-column prop="projectNo" label="项目号" width="160" show-overflow-tooltip />
        <el-table-column prop="currentVersion" label="版本" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATE_TAG[row.lifecycleState] || 'info'" size="small">
              {{ STATE_LABEL[row.lifecycleState] || row.lifecycleState }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="密级" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.secretLevel === 'SECRET'" type="danger" size="small">保密</el-tag>
            <span v-else>{{ row.secretLevel === 'PUBLIC' ? '公开' : '内部' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="责任人" width="110" show-overflow-tooltip />
        <el-table-column label="检出" width="110" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.checkedOutBy" :content="'锁定时间 ' + (row.checkedOutAt || '')">
              <el-tag type="warning" size="small">{{ row.checkedOutBy }}</el-tag>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="!row.checkedOutBy" link type="warning" size="small"
                       :disabled="isReadOnly(row)" @click="doCheckout(row)">检出</el-button>
            <el-button v-else-if="row.checkedOutBy === me" link type="success" size="small"
                       @click="openCheckin(row)">检入</el-button>
            <el-button link type="primary" size="small" @click="openStateDialog(row)">流转</el-button>
            <el-button link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="reload" />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑文档' : '新建文档'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="编号" prop="docNo">
          <el-input v-model="form.docNo" placeholder="留空自动生成（DOC+日期+流水）" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="例：XX 灯体结构图" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.docType" style="width: 100%">
            <el-option v-for="(v, k) in DOC_TYPES" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="料号">
              <el-input v-model="form.partNo" placeholder="可空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目号">
              <el-input v-model="form.projectNo" placeholder="可空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="密级">
              <el-select v-model="form.secretLevel" style="width: 100%">
                <el-option label="公开" value="PUBLIC" />
                <el-option label="内部" value="INTERNAL" />
                <el-option label="保密" value="SECRET" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-input v-model="form.owner" placeholder="留空=当前用户" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 流转 -->
    <el-dialog v-model="stateVisible" title="五区流转" width="460px">
      <div class="state-tip">
        当前状态：<el-tag :type="STATE_TAG[stateRow.lifecycleState] || 'info'">
          {{ STATE_LABEL[stateRow.lifecycleState] }}
        </el-tag>
      </div>
      <el-form label-width="90px">
        <el-form-item label="目标状态">
          <el-select v-model="stateForm.state" placeholder="请选择" style="width: 100%">
            <el-option v-for="s in nextStates" :key="s" :label="STATE_LABEL[s]" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="stateForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div v-if="!nextStates.length" class="muted">当前状态没有可流转目标</div>
      <template #footer>
        <el-button @click="stateVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!stateForm.state" @click="submitState">确定</el-button>
      </template>
    </el-dialog>

    <!-- 检入：上传新版本 -->
    <el-dialog v-model="checkinVisible" title="检入（上传新版本）" width="520px">
      <el-upload :auto-upload="false" :limit="1" :on-change="onPickFile" v-model:file-list="fileList">
        <el-button type="primary" plain>选择文件</el-button>
        <template #tip>
          <div class="muted">上传成功后自动生成新版本并解锁；版本号留空则主版本 +1</div>
        </template>
      </el-upload>
      <el-form label-width="90px" style="margin-top: 12px">
        <el-form-item label="版本号">
          <el-input v-model="checkinForm.version" placeholder="留空自动 +1，例 V2.0" />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="checkinForm.changeNote" type="textarea" :rows="2" placeholder="本次改了什么" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinVisible = false">取消</el-button>
        <el-button type="primary" :loading="checkinLoading" @click="submitCheckin">上传并检入</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail.docNo + '　' + detail.title" size="60%">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="类型">{{ DOC_TYPES[detail.docType] || detail.docType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="STATE_TAG[detail.lifecycleState] || 'info'" size="small">
            {{ STATE_LABEL[detail.lifecycleState] }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="料号">{{ detail.partNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="项目号">{{ detail.projectNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ detail.currentVersion }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detail.owner }}</el-descriptions-item>
        <el-descriptions-item label="密级">{{ detail.secretLevel }}</el-descriptions-item>
        <el-descriptions-item label="检出">{{ detail.checkedOutBy || '未锁定' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-actions">
        <el-button v-if="detail.checkedOutBy === me" size="small" @click="cancelCheckoutOf(detail)">
          取消我的检出
        </el-button>
        <el-button v-if="detail.checkedOutBy === me" type="primary" size="small" @click="openCheckin(detail)">
          检入新版本
        </el-button>
        <el-button v-if="!detail.checkedOutBy && !isReadOnly(detail)" size="small" @click="doCheckout(detail)">
          检出
        </el-button>
        <el-button size="small" @click="openStateDialog(detail)">流转</el-button>
        <el-button size="small" @click="openEdit(detail)">编辑</el-button>
      </div>

      <el-tabs v-model="detailTab" class="detail-tabs">
        <el-tab-pane label="版本链" name="versions">
          <el-timeline v-if="versions.length" class="ver-timeline">
            <el-timeline-item
              v-for="v in versions"
              :key="v.id"
              :timestamp="v.createdAt"
              placement="top"
              :type="v.isCurrent ? 'success' : 'primary'">
              <div class="ver-item">
                <div>
                  <b>{{ v.version }}{{ v.revision ? '.' + v.revision : '' }}</b>
                  <el-tag v-if="v.isCurrent" size="small" type="success" style="margin-left: 6px">当前</el-tag>
                  <el-tag size="small" :type="v.state === 'RELEASED' ? 'success' : 'info'" style="margin-left: 6px">
                    {{ v.state === 'RELEASED' ? '已发布' : '工作版' }}
                  </el-tag>
                </div>
                <div class="ver-meta">
                  文件：{{ v.fileName || '—' }}（{{ fmtSize(v.fileSize) }}）
                  <span v-if="v.createdBy">　创建：{{ v.createdBy }}</span>
                </div>
                <div v-if="v.changeNote" class="ver-note">{{ v.changeNote }}</div>
                <div class="ver-ops">
                  <el-button link type="primary" size="small" @click="downloadVersion(v)">下载</el-button>
                  <el-button link type="warning" size="small" @click="doRestore(v)">回退到此版本</el-button>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无版本，请先检出并上传文件" :image-size="80" />
        </el-tab-pane>

        <el-tab-pane label="权限" name="acl">
          <div class="acl-toolbar">
            <el-input v-model="aclForm.subjectId" placeholder="用户/角色标识" size="small" style="width: 200px" />
            <el-select v-model="aclForm.subjectType" size="small" style="width: 110px">
              <el-option label="用户" value="USER" />
              <el-option label="角色" value="ROLE" />
            </el-select>
            <el-checkbox v-model="aclForm.canRead" size="small">可读</el-checkbox>
            <el-checkbox v-model="aclForm.canWrite" size="small">可写</el-checkbox>
            <el-checkbox v-model="aclForm.canCheckout" size="small">可检出</el-checkbox>
            <el-button size="small" type="primary" @click="addAcl">添加</el-button>
          </div>
          <el-table :data="acls" size="small" border>
            <el-table-column prop="subjectType" label="类型" width="90" />
            <el-table-column prop="subjectId" label="对象" />
            <el-table-column label="读" width="60" align="center">
              <template #default="{ row }">{{ row.canRead ? '✓' : '' }}</template>
            </el-table-column>
            <el-table-column label="写" width="60" align="center">
              <template #default="{ row }">{{ row.canWrite ? '✓' : '' }}</template>
            </el-table-column>
            <el-table-column label="检出" width="70" align="center">
              <template #default="{ row }">{{ row.canCheckout ? '✓' : '' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="removeAcl(row)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDocuments, documentStats, getDocument, createDocument, updateDocument, deleteDocument,
  changeDocumentState, listVersions, checkoutDocument, checkinDocument, cancelCheckout,
  restoreVersion, listDocumentAcl, grantDocumentAcl, revokeDocumentAcl,
  uploadDocumentFile, downloadDocumentFile
} from '@/api/document'
import { useUserStore } from '@/stores/user'

const DOC_TYPES = {
  DRAWING: '图纸', STANDARD: '标准', PROCESS: '工艺文件', SPEC: '规格书',
  MANUAL: '说明书', SYSTEM: '体系文件', OTHER: '其他'
}
const STATE_LABEL = {
  WORKING: '工作区', RELEASED: '发布区', ARCHIVED: '归档区', OBSOLETE: '报废区', RECYCLE: '回收区'
}
const STATE_TAG = {
  WORKING: 'primary', RELEASED: 'success', ARCHIVED: 'info', OBSOLETE: 'warning', RECYCLE: 'danger'
}
/** 与后端 DocLifecycleState.ALLOWED 保持一致 */
const NEXT = {
  WORKING: ['RELEASED', 'RECYCLE'],
  RELEASED: ['ARCHIVED', 'OBSOLETE', 'WORKING'],
  ARCHIVED: ['RELEASED', 'RECYCLE'],
  OBSOLETE: ['RECYCLE', 'WORKING'],
  RECYCLE: ['WORKING']
}
const zones = [
  { value: 'WORKING', label: '工作区', desc: '起草/修改' },
  { value: 'RELEASED', label: '发布区', desc: '生效版本' },
  { value: 'ARCHIVED', label: '归档区', desc: '项目留存' },
  { value: 'OBSOLETE', label: '报废区', desc: '已失效' },
  { value: 'RECYCLE', label: '回收区', desc: '待清理' }
]

const userStore = useUserStore()
const me = computed(() => userStore.username || '')

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const stats = reactive({})
const query = reactive({
  keyword: '', docType: '', partNo: '', projectNo: '',
  lifecycleState: '', locked: null, pageNum: 1, pageSize: 20
})

const formVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null, docNo: '', title: '', docType: 'DRAWING',
  partNo: '', projectNo: '', secretLevel: 'INTERNAL', owner: '', remark: ''
})
const rules = { title: [{ required: true, message: '请填标题', trigger: 'blur' }] }

const stateVisible = ref(false)
const stateRow = ref({})
const stateForm = reactive({ state: '', remark: '' })
const nextStates = computed(() => NEXT[stateRow.value.lifecycleState] || [])

const checkinVisible = ref(false)
const checkinLoading = ref(false)
const checkinRow = ref({})
const checkinForm = reactive({ version: '', changeNote: '' })
const fileList = ref([])
const pickedFile = ref(null)

const detailVisible = ref(false)
const detail = ref({})
const detailTab = ref('versions')
const versions = ref([])
const acls = ref([])
const aclForm = reactive({ subjectType: 'USER', subjectId: '', canRead: true, canWrite: false, canCheckout: false })

function isReadOnly(row) {
  return ['RELEASED', 'ARCHIVED', 'OBSOLETE'].includes(row.lifecycleState)
}

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (params.locked === null) delete params.locked
    if (!params.lifecycleState) delete params.lifecycleState
    if (!params.docType) delete params.docType
    const res = await pageDocuments(params)
    rows.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const res = await documentStats()
  Object.keys(stats).forEach(k => delete stats[k])
  Object.assign(stats, res.data || {})
}

function reload() {
  query.pageNum = 1
  load()
}

function resetQuery() {
  Object.assign(query, {
    keyword: '', docType: '', partNo: '', projectNo: '',
    lifecycleState: '', locked: null, pageNum: 1
  })
  load()
}

function pickZone(v) {
  query.lifecycleState = query.lifecycleState === v ? '' : v
  query.locked = null
  reload()
}

function pickLocked() {
  query.locked = query.locked === true ? null : true
  query.lifecycleState = ''
  reload()
}

function openCreate() {
  Object.assign(form, {
    id: null, docNo: '', title: '', docType: 'DRAWING',
    partNo: query.partNo || '', projectNo: query.projectNo || '',
    secretLevel: 'INTERNAL', owner: '', remark: ''
  })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, docNo: row.docNo, title: row.title, docType: row.docType,
    partNo: row.partNo || '', projectNo: row.projectNo || '',
    secretLevel: row.secretLevel || 'INTERNAL', owner: row.owner || '', remark: row.remark || ''
  })
  formVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateDocument({ ...form })
      ElMessage.success('已保存')
    } else {
      await createDocument({ ...form, id: null })
      ElMessage.success('已创建')
    }
    formVisible.value = false
    load(); loadStats()
  } finally {
    saving.value = false
  }
}

async function doDelete(row) {
  await ElMessageBox.confirm(
    `确认删除文档 ${row.docNo}？删除后版本链不再展示。`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  load(); loadStats()
}

async function doCheckout(row) {
  await checkoutDocument(row.id)
  ElMessage.success('已检出，你现在可以上传新版本')
  load()
  if (detailVisible.value && detail.value.id === row.id) refreshDetail(row.id)
}

async function cancelCheckoutOf(row) {
  await cancelCheckout(row.id)
  ElMessage.success('已取消检出')
  load(); refreshDetail(row.id)
}

function openStateDialog(row) {
  stateRow.value = row
  stateForm.state = ''
  stateForm.remark = ''
  stateVisible.value = true
}

async function submitState() {
  await changeDocumentState(stateRow.value.id, stateForm.state, stateForm.remark)
  ElMessage.success('状态已更新')
  stateVisible.value = false
  load(); loadStats()
  if (detailVisible.value && detail.value.id === stateRow.value.id) refreshDetail(stateRow.value.id)
}

function openCheckin(row) {
  checkinRow.value = row
  checkinForm.version = ''
  checkinForm.changeNote = ''
  fileList.value = []
  pickedFile.value = null
  checkinVisible.value = true
}

function onPickFile(file) {
  pickedFile.value = file.raw
}

async function submitCheckin() {
  if (!pickedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  checkinLoading.value = true
  try {
    const up = await uploadDocumentFile(pickedFile.value, checkinRow.value.partNo)
    await checkinDocument(checkinRow.value.id, {
      fileId: up.data.id,
      version: checkinForm.version || null,
      changeNote: checkinForm.changeNote || null
    })
    ElMessage.success('已检入并解锁')
    checkinVisible.value = false
    load(); loadStats()
    if (detailVisible.value && detail.value.id === checkinRow.value.id) refreshDetail(checkinRow.value.id)
  } finally {
    checkinLoading.value = false
  }
}

async function openDetail(row) {
  await refreshDetail(row.id)
  detailVisible.value = true
  detailTab.value = 'versions'
}

async function refreshDetail(id) {
  detail.value = (await getDocument(id)).data || {}
  versions.value = (await listVersions(id)).data || []
  try {
    acls.value = (await listDocumentAcl(id)).data || []
  } catch (e) {
    // 无 document:acl 权限时不阻断详情查看
    acls.value = []
  }
}

async function doRestore(v) {
  await ElMessageBox.confirm(
    `将 ${v.version}${v.revision ? '.' + v.revision : ''} 复制为新的当前版本？历史版本不会被删除。`,
    '回退确认', { type: 'warning' })
  await restoreVersion(detail.value.id, v.id, `回退自 ${v.version}`)
  ElMessage.success('已生成新版本')
  refreshDetail(detail.value.id); load()
}

async function downloadVersion(v) {
  if (!v.fileId) {
    ElMessage.warning('该版本没有关联文件')
    return
  }
  await downloadDocumentFile(v.fileId, v.fileName || `${detail.value.docNo}_${v.version}`)
}

async function addAcl() {
  if (!aclForm.subjectId) {
    ElMessage.warning('请填用户或角色标识')
    return
  }
  await grantDocumentAcl(detail.value.id, [{ ...aclForm }])
  ElMessage.success('已授权')
  acls.value = (await listDocumentAcl(detail.value.id)).data || []
}

async function removeAcl(row) {
  await revokeDocumentAcl(detail.value.id, row.id)
  ElMessage.success('已撤销')
  acls.value = (await listDocumentAcl(detail.value.id)).data || []
}

function fmtSize(n) {
  if (!n) return '0 B'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.doc-page { padding: 12px; }
.zone-row { margin-bottom: 12px; }
.zone-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  background: #fff;
  transition: all .15s;
  margin-bottom: 8px;
}
.zone-card:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64, 158, 255, .15); }
.zone-card.active { border-color: #409eff; background: #ecf5ff; }
.zone-name { font-size: 13px; color: #606266; }
.zone-count { font-size: 22px; font-weight: 600; color: #303133; line-height: 1.3; }
.zone-desc { font-size: 12px; color: #909399; }
.filter-card { margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.muted { color: #909399; font-size: 12px; }
.detail-actions { margin: 12px 0; display: flex; gap: 8px; flex-wrap: wrap; }
.ver-item { padding-bottom: 4px; }
.ver-meta { font-size: 12px; color: #606266; margin-top: 2px; }
.ver-note { font-size: 12px; color: #909399; margin-top: 2px; white-space: pre-wrap; }
.ver-ops { margin-top: 2px; }
.acl-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.state-tip { margin-bottom: 12px; font-size: 13px; color: #606266; }
</style>
