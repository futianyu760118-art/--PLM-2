<template>
  <div class="page-container">
    <el-card>
      <el-descriptions v-if="ctx && ctx.objectRef" :column="4" border size="small" class="ctx-bar"
        title="引用项目（来自项目明细表所选，只读）">
        <el-descriptions-item label="项目编号">{{ ctx.fields.project_no }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ ctx.fields.project_name }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ ctx.fields.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ ctx.fields.owner }}</el-descriptions-item>
        <el-descriptions-item label="产品型号/料号">{{ ctx.fields.part_no }}</el-descriptions-item>
        <el-descriptions-item label="对象引用">
          {{ ctx.objectRef.object_type }}#{{ ctx.objectRef.object_id }}
          <el-tag size="small" type="info" effect="plain">tenant={{ ctx.tenant_id }}</el-tag>
          <el-tag size="small" type="info" effect="plain">src={{ ctx.source_system }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeType" @tab-change="onTabChange">
        <el-tab-pane v-for="d in defs" :key="d.type" :label="d.label" :name="d.type" />
      </el-tabs>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable @keyup.enter="loadData" /></el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable @keyup.enter="loadData" /></el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button type="primary" plain icon="Plus" @click="openDialog()">新增</el-button>
          <el-button icon="Download" @click="doExport">导出</el-button>
          <el-upload style="display:inline-block;margin-left:10px" :show-file-list="false" :http-request="doImport" accept=".xlsx,.xls">
            <el-button icon="Upload">导入</el-button>
          </el-upload>
          <el-button type="success" plain icon="MagicStick" style="margin-left:10px" @click="smartPick">智能导入</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe size="small" height="calc(100vh - 340px)">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column v-for="c in curCols" :key="c.key" :prop="c.key" :label="c.label" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px" top="6vh">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12" v-for="c in curCols" :key="c.key">
            <el-form-item :label="c.label">
              <el-input v-if="c.type==='text'" v-model="form[c.key]" />
              <el-input v-else-if="c.type==='number'" v-model="form[c.key]" type="number" />
              <el-date-picker v-else-if="c.type==='date'" v-model="form[c.key]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              <el-select v-else-if="c.type==='select'" v-model="form[c.key]" style="width:100%" clearable>
                <el-option v-for="o in (c.options||'').split(',')" :key="o" :label="o" :value="o" />
              </el-select>
              <el-input v-else v-model="form[c.key]" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 智能导入：多Sheet识别 + 表头预览 + 目标调整 -->
    <el-dialog v-model="smart.visible" title="智能导入（多 Sheet 自动识别）" width="860px" top="5vh">
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        title="系统已按 Sheet 名称/表头自动匹配目标工作表，可手动调整后导入；当前有项目筛选时自动补项目编号" />
      <el-table :data="smart.sheets" size="small" border>
        <el-table-column prop="sheetName" label="Sheet" width="130" />
        <el-table-column label="表头预览" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.headers || []).slice(0, 8).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="70" align="center" />
        <el-table-column label="导入到" width="220">
          <template #default="{ row }">
            <el-select v-model="row.target" size="small">
              <el-option label="-- 跳过 --" value="" />
              <el-option v-for="d in defs" :key="d.type" :label="d.label" :value="d.type" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="smart.visible = false">取消</el-button>
        <el-button type="primary" :loading="smart.busy" @click="smartRun">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getSheetMeta, getSheetContext, pageSheet, createSheet, updateSheet, deleteSheet, exportSheet, importSheet } from '@/api/project'

const ctx = ref(null)

const route = useRoute()
const preset = ref(null)

const defs = ref([])
const activeType = ref('')
const activeDef = computed(() => defs.value.find(d => d.type === activeType.value) || { cols: [] })
const curCols = computed(() => activeDef.value.cols || [])
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', projectNo: '' })
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})

function onTabChange() { query.pageNum = 1; loadData() }

async function loadData() {
  if (!activeType.value) return
  loading.value = true
  try {
    const res = await pageSheet(activeType.value, query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑 - ' + activeDef.value.label }
  else {
    if (preset.value) Object.assign(form, preset.value)
    if (ctx.value && ctx.value.fields) {
      // 项目信息全量预填：与表单列同名的字段自动带入（project_no/project_name/customer_name/owner 等）
      const f = ctx.value.fields
      Object.keys(f).forEach(k => { if (f[k] != null && f[k] !== '' && !(k in form)) form[k] = f[k] })
      if (!form.project_no && f.project_no) form.project_no = f.project_no
      if (!form.model && f.part_no) form.model = f.part_no
    }
    // 兜底：ctx 未加载时直接用当前筛选的项目编号
    if (!form.project_no && query.projectNo) form.project_no = query.projectNo
    dialog.title = '新增 - ' + activeDef.value.label
  }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateSheet(activeType.value, form); ElMessage.success('修改成功') }
    else { await createSheet(activeType.value, form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('删除该记录?', '警告', { type: 'warning' })
  await deleteSheet(activeType.value, row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function doExport() { await exportSheet(activeType.value); }

async function doImport(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  try {
    const res = await importSheet(activeType.value, fd)
    ElMessage.success('导入 ' + (res.data?.imported || 0) + ' 条')
    loadData()
  } catch (e) { ElMessage.error('导入失败') }
}

// ===== 智能导入：多 Sheet 自动识别（参考 EBMS 询价多Sheet导入模式） =====
const smart = reactive({ visible: false, busy: false, sheets: [] })

// Sheet 名/表头 → 工作表类型 的关键词规则
const SHEET_RULES = [
  { type: 'spec', name: /规格/i, heads: /光源|光效|色温|显指|光束角|防水等级|反光杯|线规/ },
  { type: 'config', name: /配置/i, heads: /壳体材料|支架|把 手|把手|光通|输入电压|芯片方案|认证要求|UV测试/ },
  { type: 'plan', name: /计划/i, heads: /阶段|开始时间|结束时间|负责人/ },
  { type: 'basebom', name: /BOM|物料/i, heads: /物料编码|物料名称|用量|单位/ },
  { type: 'sample', name: /样品/i, heads: /样品编号|样品名称|用途|需求数量/ },
  { type: 'review', name: /评审/i, heads: /评审类型|评审日期|主持人|结论/ },
  { type: 'test', name: /测试|送检/i, heads: /测试项目|标准要求|实际值|判定/ },
  { type: 'trial', name: /试产/i, heads: /试产类型|试产数量|不良率|主要问题/ },
  { type: 'shipment', name: /出货|验货/i, heads: /订单编号|出货日期|验货日期|验货项目/ },
]

function guessSheetTarget(sheetName, headers) {
  const name = String(sheetName || '')
  for (const r of SHEET_RULES) if (r.name.test(name)) return r.type
  const h = (headers || []).join(',')
  for (const r of SHEET_RULES) if (r.heads.test(h)) return r.type
  return ''
}

function smartPick() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (ev) => {
      try {
        const wb = XLSX.read(ev.target.result, { type: 'array' })
        const sheets = []
        for (const sn of wb.SheetNames) {
          const data = XLSX.utils.sheet_to_json(wb.Sheets[sn], { header: 1, defval: '' })
          if (!data || !data.length) continue
          const headers = (data[0] || []).map(h => String(h).trim())
          sheets.push({ sheetName: sn, headers, rowCount: data.length - 1, data, target: guessSheetTarget(sn, headers) })
        }
        if (!sheets.length) { ElMessage.warning('未解析到有效数据'); return }
        smart.sheets = sheets
        smart.visible = true
      } catch (err) { ElMessage.error('解析失败: ' + err.message) }
    }
    reader.readAsArrayBuffer(file)
  }
  input.click()
}

async function smartRun() {
  const sels = smart.sheets.filter(s => s.target)
  if (!sels.length) { ElMessage.warning('请至少选择一个 Sheet 导入'); return }
  smart.busy = true
  let ok = 0, fail = 0
  try {
    for (const s of sels) {
      // 补项目编号：表头无项目编号且当前有筛选时，插入该列
      let rows = s.data
      if (query.projectNo && !(s.headers || []).some(h => /项目编号|项目号|project_no/i.test(h))) {
        rows = s.data.map((r, i) => (i === 0 ? [...r, '项目编号'] : [...r, query.projectNo]))
      }
      // 构建单 Sheet 文件上传（后端 FuzzyMatcher 自动做表头→字段映射）
      const ws = XLSX.utils.aoa_to_sheet(rows)
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '数据')
      const blob = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
      const fd = new FormData()
      fd.append('file', new File([blob], `${s.target}_${s.sheetName}.xlsx`, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
      try {
        const res = await importSheet(s.target, fd)
        ok += res.data?.imported || 0
      } catch (e) { fail++ }
    }
    ElMessage.success(`智能导入完成：成功 ${ok} 条${fail ? `，失败 Sheet ${fail} 个` : ''}；节点完成状态已自动回填`)
    smart.visible = false
    loadData()
  } finally { smart.busy = false }
}

onMounted(async () => {
  await initFromRoute()
})

// keep-alive 缓存：从明细表等页面带新 query 二次进入时，重新按 query 初始化
watch(() => route.query, async () => {
  const q = route.query
  if (q.projectNo || q.type || q.new) { await initFromRoute() }
}, { deep: true })

async function initFromRoute() {
  const res = await getSheetMeta()
  defs.value = res.data || []
  const q = route.query
  if (q.projectNo) query.projectNo = q.projectNo
  if (query.projectNo) { try { ctx.value = (await getSheetContext(query.projectNo)).data } catch (e) { ctx.value = null } }
  if (q.preset) { try { preset.value = JSON.parse(decodeURIComponent(q.preset)) } catch (e) { preset.value = null } }
  let t = q.type
  if (!t || !defs.value.find(d => d.type === t)) t = defs.value[0]?.type
  if (t) activeType.value = t
  if (activeType.value) loadData()
  if (q.new === '1' && activeType.value) openDialog()
}
</script>
