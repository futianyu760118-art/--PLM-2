<template>
  <div class="batch-import">
    <div class="dropzone"
      :class="{ over: dragover }"
      @dragover.prevent="dragover = true"
      @dragleave.prevent="dragover = false"
      @drop.prevent="onDrop">
      <el-icon class="dz-icon"><UploadFilled /></el-icon>
      <p class="dz-main">将文件拖拽到此处，或 <label class="dz-browse">点击选择文件
        <input type="file" :accept="accept" @change="onPick" style="display:none" />
      </label></p>
      <p class="dz-hint">支持 Excel(.xlsx/.xls)、CSV(.csv)、PDF(.pdf)；单文件 ≤ 20MB</p>
    </div>

    <div class="btns">
      <el-button size="small" @click="downloadTemplate('csv')">下载CSV模板</el-button>
      <span v-if="file" class="picked">
        已选：<b>{{ file.name }}</b>
        <el-button type="primary" size="small" :loading="uploading" @click="doImport">开始导入</el-button>
      </span>
    </div>

    <el-alert v-if="result" :type="resultType" :closable="false" style="margin-top:10px">
      <div v-if="result.parsed != null">解析行数：{{ result.parsed }}</div>
      <div>成功：{{ result.success }}　跳过：{{ result.skipped }}</div>
      <div v-if="result.errors && result.errors.length">
        失败 {{ result.errors.length }} 条：{{ result.errors.slice(0, 5).join('；') }}
      </div>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  endpoint: { type: String, required: true },
  accept: { type: String, default: '.xlsx,.xls,.csv,.pdf' },
  headers: { type: Array, default: () => [] }
})
const emit = defineEmits(['done'])

const dragover = ref(false)
const file = ref(null)
const uploading = ref(false)
const result = ref(null)
const resultType = computed(() => {
  if (!result.value) return 'info'
  return (result.value.errors && result.value.errors.length) ? 'warning' : 'success'
})

function onPick(e) { setFile(e.target.files[0]); e.target.value = '' }
function onDrop(e) { dragover.value = false; setFile(e.dataTransfer.files[0]) }
function setFile(f) {
  if (!f) return
  const ok = /\.(xlsx|xls|csv|pdf)$/i.test(f.name)
  if (!ok) { ElMessage.warning('仅支持 xlsx/xls/csv/pdf'); return }
  file.value = f; result.value = null
}

async function doImport() {
  if (!file.value) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    const res = await request({ url: props.endpoint, method: 'post', data: fd, headers: { 'Content-Type': 'multipart/form-data' } })
    result.value = res.data || {}
    ElMessage.success(`导入完成：成功 ${result.value.success || 0}，跳过 ${result.value.skipped || 0}`)
    emit('done', result.value)
  } catch (e) {
    // 拦截器已提示
  } finally { uploading.value = false }
}

function downloadTemplate(fmt) {
  const hs = props.headers.length ? props.headers : ['料号', '物料名称', '英文名称', '物料类型', '产品类型', '规格', '单位', '项目号']
  const csv = '\uFEFF' + hs.join(',') + '\n' + hs.map(() => '').join(',') + '\n'
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url; a.download = `import_template.${fmt}`; a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.dropzone {
  border: 2px dashed #c0c4cc; border-radius: 10px; padding: 28px 16px; text-align: center;
  background: #fafafa; transition: all .2s; cursor: pointer;
}
.dropzone.over { border-color: #409eff; background: #ecf5ff; }
.dz-icon { font-size: 40px; color: #909399; }
.dz-main { margin: 8px 0 4px; color: #606266; }
.dz-browse { color: #409eff; cursor: pointer; text-decoration: underline; }
.dz-hint { margin: 0; color: #909399; font-size: 12px; }
.btns { margin-top: 12px; display: flex; align-items: center; gap: 10px; }
.picked { color: #606266; font-size: 13px; display: inline-flex; align-items: center; gap: 8px; }
</style>
