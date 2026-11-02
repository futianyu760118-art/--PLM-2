<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="对象类型">
          <el-select v-model="query.objectType" clearable filterable placeholder="全部" style="width:180px">
            <el-option v-for="t in objectTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="规则编码 / 名称" style="width:200px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button @click="openPreview">预览编号</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增规则</el-button>
        <span class="tip">规则改完立即生效，无需发版。SEQ 段每次取号实时读 sys_sequence。</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="ruleCode" label="规则编码" min-width="150" />
        <el-table-column prop="objectType" label="对象类型" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="pattern" label="格式描述" min-width="170" show-overflow-tooltip />
        <el-table-column prop="seqKey" label="序列键" width="140" />
        <el-table-column prop="resetPolicy" label="重置" width="90" align="center" />
        <el-table-column prop="sampleCode" label="示例" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="row.status==='ACTIVE'?'success':(row.status==='DRAFT'?'info':'danger')">
              {{ row.status==='ACTIVE'?'生效':(row.status==='DRAFT'?'草稿':'停用') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link :type="row.status==='ACTIVE'?'warning':'success'" size="small"
              @click="toggleStatus(row)">{{ row.status==='ACTIVE'?'停用':'启用' }}</el-button>
            <el-button link type="danger" size="small" :disabled="row.status==='ACTIVE'"
              @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="820px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="规则编码" required><el-input v-model="form.ruleCode" :disabled="!!form.id" placeholder="如 ECR_NO_V1" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对象类型" required><el-input v-model="form.objectType" placeholder="如 ECR / PART / BOM" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列键" required><el-input v-model="form.seqKey" placeholder="sys_sequence.seq_key" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="格式描述" required>
              <el-input v-model="form.pattern" placeholder="展示用，如 ECR+yyyyMMdd+0000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重置策略">
              <el-select v-model="form.resetPolicy" style="width:100%">
                <el-option label="不重置 NEVER" value="NEVER" />
                <el-option label="按年 YEAR" value="YEAR" />
                <el-option label="按月 MONTH" value="MONTH" />
                <el-option label="按日 DAY" value="DAY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="DRAFT">草稿</el-radio>
                <el-radio value="ACTIVE">生效</el-radio>
                <el-radio value="DISABLED">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="示例编号"><el-input v-model="form.sampleCode" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许手改">
              <el-switch v-model="form.allowManualOverride" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1" :max="9999" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">分段配置（按 seq_no 顺序拼装）</el-divider>
        <el-table :data="form.segments" border size="small">
          <el-table-column label="#" width="50" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="段类型" width="130">
            <template #default="{row}">
              <el-select v-model="row.segmentType" size="small">
                <el-option label="PREFIX 序列前缀" value="PREFIX" />
                <el-option label="CONST 常量" value="CONST" />
                <el-option label="DATE 日期" value="DATE" />
                <el-option label="SEQ 序列" value="SEQ" />
                <el-option label="DICT 字典" value="DICT" />
                <el-option label="FIELD 上下文" value="FIELD" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="值/格式" min-width="150">
            <template #default="{row}">
              <el-input v-model="row.valueExpr" size="small"
                :placeholder="placeholderOf(row.segmentType)" />
            </template>
          </el-table-column>
          <el-table-column label="补零宽度" width="100">
            <template #default="{row}">
              <el-input-number v-model="row.padLength" size="small" :min="1" :max="20" :controls="false"
                :disabled="row.segmentType!=='SEQ' && row.segmentType!=='DICT' && row.segmentType!=='FIELD'" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="上下文字段" min-width="130">
            <template #default="{row}">
              <el-input v-model="row.fieldPath" size="small" placeholder="如 customerCode"
                :disabled="row.segmentType!=='DICT' && row.segmentType!=='FIELD'" />
            </template>
          </el-table-column>
          <el-table-column label="变换" width="110">
            <template #default="{row}">
              <el-select v-model="row.transform" size="small" clearable
                :disabled="row.segmentType!=='DICT' && row.segmentType!=='FIELD'">
                <el-option label="原样" value="" />
                <el-option label="UPPER" value="UPPER" />
                <el-option label="LOWER" value="LOWER" />
                <el-option label="TRIM" value="TRIM" />
                <el-option label="DIGITS" value="DIGITS" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="form.segments.splice($index,1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="seg-actions">
          <el-button size="small" icon="Plus" @click="addSegment">加一段</el-button>
          <el-button size="small" @click="loadPresetSegments">套用「前缀+日期+4位序号」</el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="preview.visible" title="编号预览（不占号）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="对象类型"><el-input v-model="preview.objectType" placeholder="如 PART" /></el-form-item>
        <el-form-item label="上下文">
          <el-input v-model="preview.contextText" type="textarea" :rows="4"
            placeholder='JSON，如 {"customerCode":"631"}' />
        </el-form-item>
      </el-form>
      <el-alert v-if="preview.result" :title="preview.result" type="success" :closable="false" />
      <template #footer>
        <el-button @click="preview.visible=false">关闭</el-button>
        <el-button type="primary" :loading="preview.loading" @click="doPreview">生成预览</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCodeRule, getCodeRule, createCodeRule, updateCodeRule,
  changeCodeRuleStatus, deleteCodeRule, previewCode
} from '@/api/coderule'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const objectTypes = ref(['PART', 'ECN', 'ECR', 'BOM', 'MOLD', 'PROJECT', 'ISSUE', 'INSIGHT', 'EXPORT', 'OUTSOURCE', 'PROCESS', 'WORK_ITEM', 'INITIATION'])
const query = reactive({ objectType: '', keyword: '' })
const dialog = reactive({ visible: false, title: '' })
const preview = reactive({ visible: false, objectType: '', contextText: '{}', result: '', loading: false })

const emptyForm = () => ({
  id: null, ruleCode: '', objectType: '', name: '', versionNo: 'V1', status: 'DRAFT',
  pattern: '', seqKey: '', resetPolicy: 'NEVER', uniquenessScope: 'GLOBAL',
  previewEnabled: true, allowManualOverride: false, sampleCode: '', priority: 100,
  segments: []
})
const form = reactive(emptyForm())

async function loadData() {
  loading.value = true
  try {
    const res = await listCodeRule(query)
    tableData.value = res.data || []
  } finally { loading.value = false }
}

async function openDialog(row) {
  Object.assign(form, emptyForm())
  dialog.title = row ? '编辑编码规则' : '新增编码规则'
  if (row) {
    const res = await getCodeRule(row.id)
    const d = res.data
    Object.assign(form, {
      id: d.id, ruleCode: d.ruleCode, objectType: d.objectType, name: d.name,
      versionNo: d.versionNo, status: d.status, pattern: d.pattern, seqKey: d.seqKey,
      resetPolicy: d.resetPolicy, uniquenessScope: d.uniquenessScope,
      previewEnabled: d.previewEnabled, allowManualOverride: d.allowManualOverride,
      sampleCode: d.sampleCode, priority: d.priority,
      segments: (d.segments || []).map(s => ({
        seqNo: s.seqNo, segmentType: s.segmentType, valueExpr: s.valueExpr,
        padLength: s.padLength, fieldPath: s.fieldPath, transform: s.transform || ''
      }))
    })
  }
  dialog.visible = true
}

function addSegment() {
  form.segments.push({ segmentType: 'CONST', valueExpr: '', padLength: null, fieldPath: '', transform: '' })
}

function loadPresetSegments() {
  form.segments = [
    { segmentType: 'PREFIX', valueExpr: '', padLength: null, fieldPath: '', transform: '' },
    { segmentType: 'DATE', valueExpr: 'yyyyMMdd', padLength: null, fieldPath: '', transform: '' },
    { segmentType: 'SEQ', valueExpr: '', padLength: 4, fieldPath: '', transform: '' }
  ]
  ElMessage.info('已套用模板，如要固定前缀请把第 1 段改成 CONST')
}

function placeholderOf(type) {
  if (type === 'DATE') return '日期格式，如 yyyyMMdd / yy / yyyyMM'
  if (type === 'CONST') return '固定文本，如 HJ / .'
  if (type === 'PREFIX') return '留空，自动取序列前缀'
  if (type === 'SEQ') return '留空，取自序列键'
  if (type === 'DICT') return '取不到时的兜底值'
  return '留空'
}

async function submitForm() {
  submitting.value = true
  try {
    const payload = { ...form, segments: form.segments.map((s, i) => ({ ...s, seqNo: i + 1 })) }
    if (form.id) {
      await updateCodeRule(form.id, payload)
    } else {
      await createCodeRule(payload)
    }
    ElMessage.success('已保存，规则立即生效')
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await changeCodeRuleStatus(row.id, next)
  ElMessage.success(next === 'ACTIVE' ? '已启用' : '已停用')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleCode}」？`, '提示', { type: 'warning' })
  await deleteCodeRule(row.id)
  ElMessage.success('已删除')
  loadData()
}

function openPreview() {
  preview.visible = true
  preview.result = ''
  if (query.objectType) preview.objectType = query.objectType
}

async function doPreview() {
  let ctx = {}
  try {
    ctx = preview.contextText ? JSON.parse(preview.contextText) : {}
  } catch (e) {
    ElMessage.error('上下文不是合法 JSON')
    return
  }
  preview.loading = true
  try {
    const res = await previewCode(preview.objectType, ctx)
    preview.result = res.data.code
  } finally { preview.loading = false }
}

onMounted(loadData)
</script>

<style scoped>
.tip { margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.seg-actions { margin-top: 10px; }
</style>
