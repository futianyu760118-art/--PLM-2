<template>
  <div class="data-io-bar">
    <el-icon class="dio-icon"><Files /></el-icon>
    <span class="dio-title">{{ name || module }}</span>
    <span class="dio-sub">数据导入 / 导出 / 自检</span>
    <span class="dio-spacer"></span>
    <el-button size="small" icon="Upload" @click="openImport">导入</el-button>
    <el-button size="small" icon="Download" :loading="exporting" @click="handleExport">导出</el-button>
    <el-button size="small" icon="Document" @click="handleTemplate">模板</el-button>
    <el-button size="small" type="primary" plain icon="CircleCheck" :loading="checking" @click="handleSelfCheck">自检</el-button>
  </div>

  <!-- 导入 -->
  <el-dialog v-model="importDialog.visible" title="数据导入" width="680px">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
      <template #title>请先下载模板, 按「填写说明」Sheet 的必填/枚举/关联要求填写; 建议先「预检」再正式导入。</template>
    </el-alert>
    <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="onFileChange" :on-remove="onFileRemove" :file-list="fileList">
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">将 Excel 拖到此处, 或 <em>点击选择文件</em></div>
      <template #tip><div class="el-upload__tip">仅支持 .xlsx (使用系统模板)</div></template>
    </el-upload>

    <div v-if="importResult" class="io-result">
      <div class="io-result-head">
        <el-tag :type="importResult.failed ? 'warning' : 'success'">
          {{ importResult.dryRun ? '预检结果' : '导入结果' }}
        </el-tag>
        <span>总行 {{ importResult.total }} · 通过 {{ importResult.success }} · 失败 {{ importResult.failed }}
          <template v-if="!importResult.dryRun"> · 写入 {{ importResult.inserted }}</template>
        </span>
      </div>
      <el-table v-if="importResult.errors?.length" :data="importResult.errors" size="small" border max-height="240" style="margin-top:8px">
        <el-table-column prop="row" label="行" width="60" />
        <el-table-column prop="label" label="字段" width="110" />
        <el-table-column prop="message" label="问题" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="importDialog.visible=false">关闭</el-button>
      <el-button :loading="importing" :disabled="!file" @click="doImport(true)">预检</el-button>
      <el-button type="primary" :loading="importing" :disabled="!file" @click="doImport(false)">正式导入</el-button>
    </template>
  </el-dialog>

  <!-- 自检 -->
  <el-drawer v-model="checkDrawer.visible" :title="'数据自检 · ' + (checkResult.name || module)" size="720px">
    <template v-if="checkResult.module">
      <div class="check-summary">
        <el-progress type="dashboard" :percentage="checkResult.score" :width="120"
          :color="scoreColor(checkResult.score)">
          <template #default="{ percentage }">
            <div class="score-num">{{ percentage }}</div>
            <div class="score-lbl">健康分</div>
          </template>
        </el-progress>
        <div class="check-stats">
          <p>数据行数: <b>{{ checkResult.totalRows }}</b></p>
          <p>字段完整率: <b>{{ Math.round((checkResult.completeness || 0) * 100) }}%</b></p>
          <p>
            <el-tag type="danger" size="small">HIGH {{ checkResult.bySeverity?.HIGH || 0 }}</el-tag>
            <el-tag type="warning" size="small" style="margin-left:6px">MEDIUM {{ checkResult.bySeverity?.MEDIUM || 0 }}</el-tag>
          </p>
        </div>
      </div>

      <el-divider content-position="left">字段完整率</el-divider>
      <el-table :data="checkResult.fields" size="small" border max-height="240">
        <el-table-column prop="label" label="字段" width="140" />
        <el-table-column label="需填" width="60" align="center">
          <template #default="{row}"><el-tag v-if="row.required" type="danger" size="small">必填</el-tag></template>
        </el-table-column>
        <el-table-column label="已填/总数" width="110" align="center">
          <template #default="{row}">{{ row.filled }} / {{ row.total }}</template>
        </el-table-column>
        <el-table-column label="完整率">
          <template #default="{row}">
            <el-progress :percentage="Math.round(row.rate * 100)" :stroke-width="10"
              :color="row.rate >= 0.9 ? '#67c23a' : row.rate >= 0.6 ? '#e6a23c' : '#f56c6c'" />
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">问题清单 ({{ checkResult.issues?.length || 0 }})</el-divider>
      <el-table v-if="checkResult.issues?.length" :data="checkResult.issues" size="small" border max-height="320">
        <el-table-column prop="keyValue" label="业务键" width="130" show-overflow-tooltip />
        <el-table-column prop="label" label="字段" width="110" />
        <el-table-column label="级别" width="80" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.severity==='HIGH'?'danger':row.severity==='MEDIUM'?'warning':'info'">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="问题" />
      </el-table>
      <el-empty v-else description="未发现数据问题" :image-size="80" />
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { exportModule, downloadTemplate, importModule, selfCheckModule } from '@/api/dataio'

const props = defineProps({
  module: { type: String, required: true },
  name: { type: String, default: '' }
})

const exporting = ref(false)
const checking = ref(false)
const importing = ref(false)
const file = ref(null)
const fileList = ref([])

const importDialog = reactive({ visible: false })
const importResult = ref(null)

const checkDrawer = reactive({ visible: false })
const checkResult = ref({})

const scoreColor = (s) => s >= 90 ? '#67c23a' : s >= 70 ? '#e6a23c' : '#f56c6c'

function openImport() {
  importResult.value = null
  file.value = null
  fileList.value = []
  importDialog.visible = true
}

function onFileChange(uploadFile) {
  file.value = uploadFile.raw
  fileList.value = [uploadFile]
}
function onFileRemove() {
  file.value = null
  fileList.value = []
}

async function doImport(dryRun) {
  if (!file.value) { ElMessage.warning('请先选择文件'); return }
  importing.value = true
  try {
    const res = await importModule(props.module, file.value, dryRun)
    importResult.value = res.data
    if (dryRun) ElMessage.success('预检完成')
    else if (res.data.failed === 0) ElMessage.success(`导入成功 ${res.data.inserted} 行`)
    else ElMessage.warning(`导入完成, ${res.data.failed} 行未通过`)
  } finally { importing.value = false }
}

async function handleExport() {
  exporting.value = true
  try {
    await exportModule(props.module)
    ElMessage.success('导出完成')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally { exporting.value = false }
}

async function handleTemplate() {
  try {
    await downloadTemplate(props.module)
    ElMessage.success('模板已下载')
  } catch (e) {
    ElMessage.error(e.message || '模板下载失败')
  }
}

async function handleSelfCheck() {
  checking.value = true
  try {
    const res = await selfCheckModule(props.module)
    checkResult.value = res.data
    checkDrawer.visible = true
  } finally { checking.value = false }
}
</script>

<style lang="scss" scoped>
.data-io-bar {
  display: flex; align-items: center; gap: 8px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 8px;
  padding: 8px 14px; margin-bottom: 14px;
  .dio-icon { color: #909399; font-size: 16px; }
  .dio-title { font-size: 14px; font-weight: 600; color: #303133; }
  .dio-sub { font-size: 12px; color: #909399; }
  .dio-spacer { flex: 1; }
}
.io-result { margin-top: 12px;
  .io-result-head { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #606266; }
}
.check-summary { display: flex; align-items: center; gap: 28px; padding: 6px 4px 2px;
  .score-num { font-size: 22px; font-weight: 700; color: #303133; }
  .score-lbl { font-size: 12px; color: #909399; }
  .check-stats p { margin: 6px 0; font-size: 13px; color: #606266;
    b { color: #409eff; }
  }
}
</style>
