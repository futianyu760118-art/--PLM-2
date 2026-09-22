<template>
  <div class="page-container">
    <OperationGuide module-key="material" />
    <DataIOBar module="material" name="物料主数据" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="料号"><el-input v-model="query.partNo" placeholder="料号" clearable /></el-form-item>
        <el-form-item label="名称"><el-input v-model="query.materialName" placeholder="物料名称" clearable /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.materialType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()" v-if="userStore.hasPermission('material:add')">新增物料</el-button>
        <el-button type="warning" icon="MagicStick" @click="$router.push('/material/wizard')" v-if="userStore.hasPermission('material:add')">建档向导</el-button>
        <el-button type="success" icon="Download" @click="handleExport" v-if="userStore.hasPermission('material:export')">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe row-key="id">
        <el-table-column prop="partNo" label="料号" min-width="160" fixed />
        <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ typeMap[row.materialType] }}</template></el-table-column>
        <el-table-column prop="specification" label="规格" min-width="140" show-overflow-tooltip />
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="质量分" width="90" align="center">
          <template #default="{ row }">
            <el-button link :type="scoreColor(row._score)" size="small" @click="handleDqCheck(row)">
              {{ row._score != null ? row._score : '—' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)" v-if="row.status==='DRAFT'||row.status==='IN_REVIEW'">编辑</el-button>
            <el-button link type="success" size="small" v-if="row.status === 'DRAFT'" @click="handleReview(row)">评审</el-button>
            <el-button link type="warning" size="small" v-if="row.status === 'DRAFT' || row.status === 'IN_REVIEW'" @click="handleRelease(row)">发布</el-button>
            <el-button link type="primary" size="small" v-if="row.status==='RELEASED'" @click="handleAction(row,'to-production','转量产')">转量产</el-button>
            <el-button link type="danger" size="small" v-if="row.status==='RELEASED'||row.status==='IN_PRODUCTION'" @click="handleAction(row,'obsolete','作废')">作废</el-button>
            <el-button link type="info" size="small" v-if="row.status==='OBSOLETE'" @click="handleAction(row,'seal','封存')">封存</el-button>
            <el-button link size="small" @click="openVersions(row)">版本</el-button>
            <el-button link type="success" size="small" @click="openDetails(row)">详情</el-button>
            <el-button link type="danger" size="small" v-if="row.status === 'DRAFT'" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="640px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="料号" prop="partNo">
              <el-input v-model="form.partNo" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名"><el-input v-model="form.nameEn" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料类型" prop="materialType">
              <el-select v-model="form.materialType" style="width:100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="产品类型"><el-input v-model="form.productType" placeholder="如:投光灯" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="IP等级"><el-input v-model="form.ipRating" placeholder="如:IP66" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="form.specification" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品系列"><el-input v-model="form.productSeries" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="归属项目"><el-input v-model="form.projectNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="制造类型">
              <el-radio-group v-model="form.makeType">
                <el-radio :value="0">自制</el-radio>
                <el-radio :value="1">外购</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 物料详情: 多阶梯供应商 + 图纸号 + 变更历史(企业微信文档式) -->
    <el-drawer v-model="detailsDrawer.visible" :title="'物料详情 - ' + (detailsDrawer.partNo || '')" size="880px">
      <el-tabs v-model="detailsDrawer.tab">
        <!-- 多阶梯供应商 -->
        <el-tab-pane label="供应商阶梯" name="suppliers">
          <el-button type="primary" size="small" icon="Plus" @click="addSupplierRow" style="margin-bottom:8px">添加阶梯</el-button>
          <el-table :data="form.suppliers" border size="small" style="width:100%">
            <el-table-column label="阶梯" width="100" align="center">
              <template #default="{ row, $index }">
                <el-tag :type="$index===0?'success':$index===1?'warning':'info'" effect="dark">
                  {{ $index === 0 ? '主供' : $index === 1 ? '备选' : '试产' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="供应商编码" width="140">
              <template #default="{ row }"><el-input v-model="row.supplierCode" size="small" /></template>
            </el-table-column>
            <el-table-column label="供应商名称" min-width="180">
              <template #default="{ row }"><el-input v-model="row.supplierName" size="small" /></template>
            </el-table-column>
            <el-table-column label="单价" width="120">
              <template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" size="small" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="份额%" width="100">
              <template #default="{ row }"><el-input-number v-model="row.sharePct" :min="0" :max="100" size="small" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="交期(天)" width="100">
              <template #default="{ row }"><el-input-number v-model="row.leadTimeDays" :min="0" size="small" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="MOQ" width="100">
              <template #default="{ row }"><el-input-number v-model="row.moq" :min="0" size="small" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="form.suppliers.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-alert v-if="!form.suppliers?.length" type="info" :closable="false"
            title="未配置供应商;点上方「添加阶梯」按 主供→备选→试产 顺序排列" />
        </el-tab-pane>

        <!-- 图纸附件 -->
        <el-tab-pane label="图纸附件" name="drawing">
          <el-form label-width="100px">
            <el-form-item label="图纸编号">
              <el-input v-model="form.drawingNo" placeholder="如 DX202401" style="width:280px" />
              <span style="margin-left:8px;color:#909399;font-size:12px">变更时会自动同步到物料规格描述</span>
            </el-form-item>
            <el-form-item label="图纸版本">
              <el-input v-model="form.drawingRevision" placeholder="如 V2.0" style="width:280px" />
            </el-form-item>
            <el-form-item label="图纸文件">
              <el-upload action="/api/file/upload" :headers="uploadHeaders" :show-file-list="false"
                :before-upload="beforeUpload" :on-success="onUploadSuccess" :on-error="onUploadError">
                <el-button icon="Upload">上传图纸(PDF/DWG)</el-button>
                <span v-if="form.drawingNo" style="margin-left:8px;color:#67c23a">
                  当前: {{ form.drawingNo }}{{ form.drawingRevision ? ' ' + form.drawingRevision : '' }}
                </span>
              </el-upload>
            </el-form-item>
            <el-form-item label="规格预览">
              <el-tag effect="plain" type="info" style="font-family:monospace">
                {{ previewDescription }}
              </el-tag>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 变更历史 -->
        <el-tab-pane label="变更历史" name="history">
          <el-timeline v-if="historyList.length">
            <el-timeline-item v-for="h in historyList" :key="h.id" :timestamp="h.changedAt" placement="top">
              <el-card shadow="never">
                <template #header>
                  <div style="display:flex;justify-content:space-between">
                    <span><b>v{{ h.version }}</b> · {{ h.changeSource }} · by {{ h.changedBy }}</span>
                    <el-tag size="small">{{ h.changeType }}</el-tag>
                  </div>
                </template>
                <div v-for="(diff, idx) in parseChanges(h.changedFields)" :key="idx" class="diff-row">
                  <el-tag size="small" :type="diff.after ? 'warning' : 'info'">{{ diff.field }}</el-tag>
                  <span class="before">{{ diff.before === null || diff.before === '' ? '∅' : diff.before }}</span>
                  <el-icon><Right /></el-icon>
                  <span class="after">{{ diff.after === null || diff.after === '' ? '∅' : diff.after }}</span>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!historyList.length" description="暂无变更记录" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-drawer v-model="versionDrawer.visible" :title="'版本历史 - ' + (versionDrawer.partNo || '')" size="600px">
      <el-timeline>
        <el-timeline-item v-for="v in versionDrawer.list" :key="v.id" :timestamp="v.createdAt" placement="top">
          <el-card>
            <p><b>{{ v.versionNo }}</b> · {{ v.changeReason || '—' }}</p>
            <p v-if="v.ecnNo" style="color:#e6a23c">ECN: {{ v.ecnNo }}</p>
            <p style="color:#999;font-size:12px">by {{ v.createdBy }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!versionDrawer.list.length" description="暂无版本历史" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  pageMaterial, addMaterial, updateMaterial, deleteMaterial, releaseMaterial, submitReview,
  toProduction, obsoleteMaterial, sealMaterial, dqCheck, getVersions
} from '@/api/material'
import { createExport } from '@/api/exportx'
import { dqScores } from '@/api/metric'
import { listSuppliers, listHistory, uploadDrawing } from '@/api/ux'

const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const formRef = ref()

const typeMap = { FINISHED: '成品', SEMI: '半成品', PLASTIC: '塑胶件', HARDWARE: '五金件', STANDARD: '标准件' }
const statusMap = { DRAFT: '草稿', IN_REVIEW: '评审中', RELEASED: '正式发布', IN_PRODUCTION: '量产在用', CHANGING: '变更中', OBSOLETE: '作废', SEALED: '停产封存' }
const statusTag = (s) => ({ DRAFT: 'info', IN_REVIEW: 'warning', RELEASED: 'success', IN_PRODUCTION: 'primary', CHANGING: 'warning', OBSOLETE: 'danger', SEALED: 'info' }[s] || 'info')
const scoreColor = (s) => s == null ? '' : s >= 90 ? 'success' : s >= 70 ? 'warning' : 'danger'

const query = reactive({ pageNum: 1, pageSize: 10, partNo: '', materialName: '', materialType: '', status: '' })
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})
const rules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }]
}
const versionDrawer = reactive({ visible: false, partNo: '', list: [] })

// 物料详情 drawer
const detailsDrawer = reactive({ visible: false, partNo: '', tab: 'suppliers' })
const historyList = ref([])
// 图纸预览描述(规格 + 图纸号)
const previewDescription = computed(() => {
  const tag = form.drawingNo ? '图纸 ' + form.drawingNo + (form.drawingRevision ? ' ' + form.drawingRevision : '') : ''
  if (!tag) return form.specification || '(空)'
  return form.specification ? `${form.specification} | ${tag}` : tag
})
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${userStore.token || ''}` }))

function addSupplierRow() {
  if (!form.suppliers) form.suppliers = []
  form.suppliers.push({ tierRank: form.suppliers.length + 1, supplierCode: '', supplierName: '', price: 0, sharePct: 0, leadTimeDays: 0, moq: 0, currency: 'CNY', enabled: true })
}

function parseChanges(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

async function openDetails(row) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  if (!form.suppliers) form.suppliers = []
  detailsDrawer.partNo = row.partNo
  detailsDrawer.tab = 'suppliers'
  detailsDrawer.visible = true
  // 加载供应商 + 历史
  try {
    const r = await listSuppliers(row.id)
    form.suppliers = r.data || []
  } catch { form.suppliers = [] }
  loadHistory(row.partNo)
  detailsDrawer.tab = 'suppliers'
}

async function loadHistory(partNo) {
  try {
    const r = await listHistory('PART', partNo)
    historyList.value = r.data || []
  } catch { historyList.value = [] }
}

function beforeUpload(file) {
  const ok = /\.(pdf|dwg|dxf|step|stp)$/i.test(file.name) || file.type === 'application/pdf'
  if (!ok) { ElMessage.warning('仅支持 PDF/DWG/DXF/STEP 图纸文件'); return false }
  if (file.size > 100 * 1024 * 1024) { ElMessage.warning('文件大小不可超过100MB'); return false }
  return true
}

function onUploadSuccess(res) {
  if (res?.data?.id) {
    ElMessage.success('图纸已上传,ID=' + res.data.id)
    if (!form.drawingNo) form.drawingNo = res.data.fileName?.split('.')[0] || 'DX' + Date.now()
  }
}

function onUploadError() { ElMessage.error('图纸上传失败') }

async function loadData() {
  loading.value = true
  try {
    const res = await pageMaterial(query)
    tableData.value = res.data.records
    total.value = res.data.total
    loadScores()
  } finally {
    loading.value = false
  }
}

async function loadScores() {
  try {
    const res = await dqScores('PART')
    const map = {}
    ;(res.data || []).forEach(s => { map[s.objectId] = s.score0_100 ?? s.score_0_100 })
    tableData.value.forEach(r => { r._score = map[String(r.id)] ?? null })
  } catch {}
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, pageSize: 10, partNo: '', materialName: '', materialType: '', status: '' })
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(form, JSON.parse(JSON.stringify(row)))
    dialog.title = '编辑物料'
  } else {
    resetForm()
    dialog.title = '新增物料'
  }
  dialog.visible = true
}

function resetForm() {
  Object.keys(form).forEach(k => delete form[k])
  form.materialType = 'FINISHED'
  form.makeType = 0
  form.unit = 'PCS'
  formRef.value?.clearValidate()
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) {
      await updateMaterial(form)
      ElMessage.success('修改成功')
    } else {
      await addMaterial(form)
      ElMessage.success('新增成功')
    }
    dialog.visible = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleReview(row) {
  await ElMessageBox.confirm(`提交物料 [${row.partNo}] 评审?`, '确认', { type: 'info' })
  await submitReview(row.id)
  ElMessage.success('已提交评审')
  loadData()
}

async function handleRelease(row) {
  await ElMessageBox.confirm(`确认发布物料 [${row.partNo}]? 发布后基础信息锁定,需走ECN变更`, '确认', { type: 'warning' })
  await releaseMaterial(row.id)
  ElMessage.success('发布成功')
  loadData()
}

async function handleAction(row, action, label) {
  await ElMessageBox.confirm(`确认对 [${row.partNo}] 执行「${label}」?`, '确认', { type: 'warning' })
  const fnMap = { 'to-production': toProduction, 'obsolete': obsoleteMaterial, 'seal': sealMaterial }
  await fnMap[action](row.id)
  ElMessage.success(label + '成功')
  loadData()
}

async function handleDqCheck(row) {
  const res = await dqCheck(row.id)
  const dq = res.data
  row._score = dq.score
  if (dq.blockCount > 0) {
    ElMessageBox.alert(dq.items.filter(i => i.severity === 'BLOCK' && i.result === 'FAIL').map(i => '✗ ' + i.message).join('\n'), '质量阻断', { type: 'error' })
  } else {
    ElMessage.success(`质量分: ${dq.score} | BLOCK:0 WARN:${dq.warnCount}`)
  }
}

async function openVersions(row) {
  versionDrawer.partNo = row.partNo
  versionDrawer.visible = true
  const res = await getVersions(row.id)
  versionDrawer.list = res.data || []
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除物料 [${row.partNo}]?`, '警告', { type: 'warning' })
  await deleteMaterial(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleExport() {
  try {
    const res = await createExport({ exportType: 'RESOURCE', format: 'csv', name: '物料清单', spec: { resource: 'part' } })
    if (res.data.status === 'DONE' && res.data.filePath) {
      ElMessage.success(`导出完成: ${res.data.rowCount} 行`)
      window.open(`/api/v1/exports/${res.data.id}/download`, '_blank')
    } else if (res.data.errorMsg) {
      ElMessage.error('导出失败: ' + res.data.errorMsg)
    }
  } catch {}
}

onMounted(loadData)
</script>
