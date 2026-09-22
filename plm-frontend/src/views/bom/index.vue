<template>
  <div class="page-container">
    <OperationGuide module-key="bom" />
    <DataIOBar module="bom" name="BOM产品结构" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="成品料号"><el-input v-model="query.rootPartNo" placeholder="成品料号" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
            <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openCreate" v-if="userStore.hasPermission('bom:add')">新建BOM</el-button>
        <el-button type="success" icon="MagicStick" @click="openTplDialog" v-if="userStore.hasPermission('bom:add')">套用模板创建</el-button>
        <el-button type="warning" icon="Download" @click="openBomExport" v-if="userStore.hasPermission('bom:view')">导出BOM(含子BOM)</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="bomNo" label="BOM编号" min-width="170" fixed />
        <el-table-column prop="rootPartNo" label="顶级成品料号" min-width="160" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.bomType)" effect="dark" size="small">{{ typeMap[row.bomType] || 'EBOM' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusMap[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ecnNo" label="关联ECN" min-width="140" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goTree(row)">编辑树</el-button>
            <el-button link type="warning" size="small" v-if="row.status==='DRAFT'" @click="handleRelease(row)">发布</el-button>
            <el-button link type="success" size="small" v-if="row.bomType==='EBOM' && row.status==='RELEASED'" @click="handleBuildMbom(row)">构建MBOM</el-button>
            <el-button link type="info" size="small" v-if="row.status==='RELEASED'" @click="handleBuildSbom(row)">构建SBOM</el-button>
            <el-button link size="small" @click="openWhereUsed(row.rootPartNo)">反查</el-button>
            <el-button link size="small" type="primary" @click="openCbom(row.rootPartNo)">CBOM</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建BOM" width="480px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="顶级成品料号" required>
          <el-input v-model="createForm.rootPartNo" placeholder="成品料号" />
        </el-form-item>
        <el-form-item label="BOM类型">
          <el-select v-model="createForm.bomType" style="width:100%">
            <el-option label="EBOM(设计BOM)" value="EBOM" />
            <el-option label="MBOM(制造BOM)" value="MBOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号"><el-input v-model="createForm.versionNo" placeholder="V1.0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 套用模板创建BOM -->
    <el-dialog v-model="tplDialog.visible" title="套用 BOM 大类模板(结构件/紧固件/包装/电子)" width="600px">
      <el-form label-width="100px">
        <el-form-item label="成品料号" required>
          <el-input v-model="tplDialog.rootPartNo" placeholder="成品料号,如 HJ-FL-100W" />
        </el-form-item>
        <el-form-item label="选择模板">
          <el-checkbox-group v-model="tplDialog.codes">
            <el-checkbox v-for="t in tplDialog.templates" :key="t.templateCode" :label="t.templateCode" border>
              {{ t.templateName }} ({{ categoryLabel(t.category) }})
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="可选项">
          <el-switch v-model="tplDialog.includeOptional" />
          <span style="margin-left:8px;color:#909399;font-size:12px">包含可选子件(如反光罩、泡棉)</span>
        </el-form-item>
        <el-form-item>
          <el-alert v-if="tplDialog.preview" :title="'套用预览'" type="info" :closable="false">
            <div v-for="t in tplDialog.preview" :key="t.templateCode" style="margin:6px 0">
              <b>{{ t.templateName }}</b> — {{ t.items?.length || 0 }} 个子件
              <span style="margin-left:8px;color:#909399;font-size:12px">
                ({{ t.items?.filter(i => i.isOptional).length || 0 }} 可选)
              </span>
            </div>
            <div style="margin-top:8px;color:#e6a23c">合计: {{ totalItems }} 个子件</div>
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="tplDialog.submitting" @click="submitFromTemplates">一键创建</el-button>
      </template>
    </el-dialog>

    <!-- BOM 子件递归导出 -->
    <el-dialog v-model="exportDialog.visible" title="导出 BOM(含子BOM递归)" width="500px">
      <el-form label-width="120px">
        <el-form-item label="成品料号" required>
          <el-input v-model="exportDialog.rootPartNo" placeholder="成品料号" />
        </el-form-item>
        <el-form-item label="包含子BOM">
          <el-switch v-model="exportDialog.includeSubBom" />
          <span style="margin-left:8px;color:#909399;font-size:12px">
            开启后递归到叶子子BOM的全BOM物料清单
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="exportDialog.submitting" @click="submitBomExport">导出</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="whereUsedDrawer.visible" title="Where-Used 反查" size="600px">
      <el-table :data="whereUsedDrawer.list" border size="small">
        <el-table-column prop="bomNo" label="BOM编号" width="160" />
        <el-table-column prop="rootPartNo" label="成品料号" width="140" />
        <el-table-column prop="bomType" label="类型" width="80" />
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>
      <el-empty v-if="!whereUsedDrawer.list.length" description="未被其他BOM引用" />
    </el-drawer>

    <el-drawer v-model="cbomDrawer.visible" :title="'成本BOM展开 - ' + (cbomDrawer.partNo||'')" size="780px">
      <div v-if="cbomDrawer.totalCost != null" style="margin-bottom:10px;font-size:14px">
        <b>总成本:</b> <span style="color:#e6a23c;font-size:16px">¥{{ Number(cbomDrawer.totalCost).toFixed(2) }}</span>
        <span style="margin-left:16px;color:#999">共 {{ cbomDrawer.list.length }} 行</span>
      </div>
      <el-table :data="cbomDrawer.list" border size="small">
        <el-table-column type="index" label="#" width="40" />
        <el-table-column prop="partNo" label="料号" width="140" />
        <el-table-column prop="partName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="effectiveQuantity" label="有效数量" width="80" align="right" />
        <el-table-column prop="unit" label="单位" width="55" />
        <el-table-column label="单价" width="80" align="right">
          <template #default="{row}">{{ row.unitCost != null ? '¥' + Number(row.unitCost).toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90" align="right">
          <template #default="{row}">
            <span :style="row.extendedCost > 0 ? 'color:#e6a23c;font-weight:600' : ''">
              {{ row.extendedCost != null ? '¥' + Number(row.extendedCost).toFixed(2) : '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="60" align="center">
          <template #default="{row}">{{ row.makeType===1?'外购':'自制' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!cbomDrawer.list.length" description="暂无BOM结构" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pageBom, createBom, releaseBom, buildMbom, buildSbom, whereUsed, cbomExpand } from '@/api/bom'
import { listTemplates, createFromTemplates, exportBom } from '@/api/bomTpl'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const createVisible = ref(false)
const createForm = reactive({ rootPartNo: '', versionNo: 'V1.0', bomType: 'EBOM', remark: '' })

const statusMap = { DRAFT: '草稿', REVIEWING: '评审中', RELEASED: '正式发布', CHANGING: '变更中', OBSOLETE: '作废', SEALED: '封存' }
const statusTag = (s) => ({ DRAFT: 'info', RELEASED: 'success', CHANGING: 'warning', OBSOLETE: 'danger', SEALED: 'info' }[s] || 'info')
const typeMap = { EBOM: 'EBOM', MBOM: 'MBOM', SBOM: 'SBOM' }
const typeTag = (t) => ({ EBOM: 'info', MBOM: 'success', SBOM: 'warning' }[t] || 'info')

const query = reactive({ pageNum: 1, pageSize: 10, rootPartNo: '', status: '' })
const whereUsedDrawer = reactive({ visible: false, list: [] })
const cbomDrawer = reactive({ visible: false, partNo: '', list: [], totalCost: null })

// 模板创建
const tplDialog = reactive({ visible: false, rootPartNo: '', codes: [], includeOptional: true, templates: [], preview: null, submitting: false })
const categoryLabel = (c) => ({ structure: '结构件', fastener: '紧固件', packaging: '包装', electronics: '电子电器', standard: '标准件', custom: '自定义' }[c] || c)
const totalItems = computed(() => {
  if (!tplDialog.preview) return 0
  let n = 0
  for (const t of tplDialog.preview) {
    if (!t.items) continue
    for (const i of t.items) {
      if (tplDialog.includeOptional || !i.isOptional) n++
    }
  }
  return n
})
watch(() => tplDialog.codes, async (codes) => {
  if (!codes || codes.length === 0) { tplDialog.preview = null; return }
  if (!tplDialog.templates.length) {
    const r = await listTemplates()
    tplDialog.templates = r.data || []
  }
  tplDialog.preview = tplDialog.templates.filter(t => codes.includes(t.templateCode))
})

// BOM 导出
const exportDialog = reactive({ visible: false, rootPartNo: '', includeSubBom: true, submitting: false })

async function loadData() {
  loading.value = true
  try {
    const res = await pageBom(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, pageSize: 10, rootPartNo: '', status: '' })
  loadData()
}

function openCreate() {
  createForm.rootPartNo = ''
  createForm.versionNo = 'V1.0'
  createForm.bomType = 'EBOM'
  createForm.remark = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.rootPartNo) { ElMessage.warning('请输入顶级成品料号'); return }
  submitting.value = true
  try {
    await createBom(createForm)
    ElMessage.success('BOM创建成功')
    createVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

function goTree(row) {
  router.push(`/bom/tree/${row.id}`)
}

async function handleRelease(row) {
  await ElMessageBox.confirm(`确认发布BOM [${row.bomNo}]? 发布后写版本快照`, '确认', { type: 'warning' })
  await releaseBom(row.id)
  ElMessage.success('发布成功')
  loadData()
}

async function handleBuildMbom(row) {
  await ElMessageBox.confirm(`从EBOM [${row.bomNo}] 构建MBOM?`, '确认', { type: 'info' })
  await buildMbom(row.id)
  ElMessage.success('MBOM已构建')
  loadData()
}

async function handleBuildSbom(row) {
  await ElMessageBox.confirm(`构建SBOM(售后备件)?`, '确认', { type: 'info' })
  await buildSbom(row.id)
  ElMessage.success('SBOM已构建')
  loadData()
}

async function openWhereUsed(partNo) {
  whereUsedDrawer.visible = true
  const res = await whereUsed(partNo)
  whereUsedDrawer.list = res.data || []
}

async function openCbom(partNo) {
  cbomDrawer.partNo = partNo
  cbomDrawer.visible = true
  cbomDrawer.list = []
  cbomDrawer.totalCost = null
  try {
    const res = await request({ url: `/v1/integration/parts/${partNo}/cbom`, method: 'get' })
    cbomDrawer.list = res.data?.lines || []
    cbomDrawer.totalCost = res.data?.totalCost ?? null
  } catch {
    const res2 = await cbomExpand(partNo)
    cbomDrawer.list = res2.data || []
  }
}

// ===== 套用模板 =====
async function openTplDialog() {
  tplDialog.rootPartNo = ''
  tplDialog.codes = ['STRUCTURE_DEFAULT']
  tplDialog.includeOptional = true
  tplDialog.preview = null
  tplDialog.visible = true
  if (!tplDialog.templates.length) {
    const r = await listTemplates()
    tplDialog.templates = r.data || []
  }
}

async function submitFromTemplates() {
  if (!tplDialog.rootPartNo) { ElMessage.warning('请输入成品料号'); return }
  if (!tplDialog.codes.length) { ElMessage.warning('请至少勾选一个模板'); return }
  tplDialog.submitting = true
  try {
    const r = await createFromTemplates(tplDialog.rootPartNo, tplDialog.codes, tplDialog.includeOptional)
    ElMessage.success(`BOM[${r.data.bomNo}]创建成功,共 ${totalItems.value} 行明细`)
    tplDialog.visible = false
    loadData()
  } finally { tplDialog.submitting = false }
}

// ===== BOM 递归导出 =====
function openBomExport() {
  exportDialog.rootPartNo = query.rootPartNo || ''
  exportDialog.includeSubBom = true
  exportDialog.visible = true
}

async function submitBomExport() {
  if (!exportDialog.rootPartNo) { ElMessage.warning('请输入成品料号'); return }
  exportDialog.submitting = true
  try {
    const r = await exportBom({ rootPartNo: exportDialog.rootPartNo, includeSubBom: exportDialog.includeSubBom })
    ElMessage.success(`已创建导出任务 ${r.data.exportNo},前往导出中心下载`)
    exportDialog.visible = false
  } finally { exportDialog.submitting = false }
}

onMounted(loadData)
</script>
