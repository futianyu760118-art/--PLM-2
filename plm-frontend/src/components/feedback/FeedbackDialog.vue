<template>
  <el-drawer
    :model-value="modelValue"
    title="问题反馈"
    direction="rtl"
    size="520px"
    :modal="false"
    :append-to-body="true"
    :close-on-click-modal="false"
    @update:model-value="v => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <el-tabs v-model="tab">
      <el-tab-pane label="提交反馈" name="form">
        <div v-if="receipt" class="receipt">
          <el-icon class="receipt-icon"><CircleCheckFilled /></el-icon>
          <div class="receipt-title">提交成功</div>
          <div class="receipt-row">反馈编号：#{{ receipt.id }}</div>
          <div class="receipt-row">状态：待处理</div>
          <el-button type="primary" plain @click="startNew">再提一条</el-button>
        </div>

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="80px">
          <el-form-item label="类型" prop="type">
            <el-radio-group v-model="form.type">
              <el-radio v-for="t in TYPE_OPTS" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="严重度" prop="severity">
            <el-radio-group v-model="form.severity">
              <el-radio v-for="s in SEVERITY_OPTS" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="256" show-word-limit placeholder="一句话概括问题或建议" />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="2000" show-word-limit
              placeholder="请详细描述问题现象、复现步骤或建议内容" />
          </el-form-item>
          <el-form-item label="附件">
            <el-upload :show-file-list="false" :http-request="doUpload" :disabled="uploading">
              <el-button size="small" :loading="uploading">上传附件</el-button>
            </el-upload>
            <div v-if="form.attachments.length" class="att-list">
              <el-tag v-for="a in form.attachments" :key="a.key" closable @close="removeAtt(a)">
                {{ a.name }}
              </el-tag>
            </div>
          </el-form-item>
          <el-form-item label="所在页面">
            <el-input :model-value="form.pagePath" readonly disabled />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="反馈记录" name="records">
        <div v-loading="recordsLoading" class="fb-records">
          <div v-for="r in records" :key="r.id" class="fb-record">
            <div class="fb-rec-head">
              <span class="fb-rec-id">#{{ r.id }}</span>
              <span class="fb-rec-title">{{ r.title }}</span>
              <el-tag size="small" :type="statusTag(r.status)">{{ statusLabel(r.status) }}</el-tag>
              <el-tag size="small" :type="aiTag(r.ai_status)">{{ aiLabel(r.ai_status) }}</el-tag>
            </div>
            <div class="fb-rec-desc">{{ r.description || '—' }}</div>
            <div v-if="r.reply" class="fb-rec-reply">
              <div class="fb-rec-meta">{{ r.replied_by || '系统' }} · {{ fmt(r.replied_at) }}</div>
              <pre class="fb-rec-text">{{ r.reply }}</pre>
            </div>
          </div>
          <el-empty v-if="!records.length" description="暂无反馈记录" :image-size="70" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <template v-if="tab === 'form' && !receipt" #footer>
      <div class="fd-footer">
        <el-button link type="primary" @click="emit('minimize')">收起</el-button>
        <span class="fd-spacer"></span>
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" class="submit-btn" :loading="submitting" @click="handleSubmit">提交</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createFeedback, uploadFeedbackFile, listFeedback } from '@/api/feedback'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'minimize'])

const route = useRoute()
const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const receipt = ref(null)
const tab = ref('form')

const records = ref([])
const recordsLoading = ref(false)

const TYPE_OPTS = [
  { value: 'issue', label: '问题' },
  { value: 'suggestion', label: '建议' },
  { value: 'inquiry', label: '咨询' }
]
const SEVERITY_OPTS = [
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' }
]

const STATUS_META = { pending: ['待处理', 'warning'], processing: ['处理中', 'primary'], resolved: ['已解决', 'success'], closed: ['已关闭', 'info'] }
const AI_META = { none: ['未派单', 'info'], queued: ['排队中', 'warning'], running: ['实现中', 'primary'], done: ['已实现', 'success'], failed: ['失败', 'danger'] }
const statusLabel = s => (STATUS_META[s] || ['', 'info'])[0]
const statusTag = s => (STATUS_META[s] || ['', 'info'])[1]
const aiLabel = s => (AI_META[s] || ['未派单', 'info'])[0]
const aiTag = s => (AI_META[s] || ['未派单', 'info'])[1]
const fmt = t => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const form = reactive({ type: 'issue', severity: 'low', title: '', description: '', pagePath: '', attachments: [] })

const rules = computed(() => ({
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  severity: [{ required: true, message: '请选择严重度', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  description: [{ required: true, message: '请填写描述', trigger: 'blur' }]
}))

function onOpen() {
  receipt.value = null
  tab.value = 'form'
  form.pagePath = route.path
  loadRecords()
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await listFeedback({ page: 1, pageSize: 100 })
    records.value = res.data?.items || []
  } finally {
    recordsLoading.value = false
  }
}

function startNew() {
  receipt.value = null
  Object.assign(form, { type: 'issue', severity: 'low', title: '', description: '', pagePath: route.path, attachments: [] })
  nextTick(() => formRef.value?.clearValidate())
}

async function doUpload(opt) {
  uploading.value = true
  try {
    const res = await uploadFeedbackFile(opt.file)
    const f = res.data
    form.attachments.push({
      key: String(f.id),
      url: `/file/${f.id}/download`,
      name: f.fileName,
      size: opt.file.size,
      mime: opt.file.type || ''
    })
    ElMessage.success('附件已上传')
    opt.onSuccess && opt.onSuccess()
  } catch (e) {
    opt.onError && opt.onError(e)
  } finally {
    uploading.value = false
  }
}

function removeAtt(a) {
  form.attachments = form.attachments.filter(x => x.key !== a.key)
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const res = await createFeedback({ ...form })
    receipt.value = res.data
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.receipt { padding: 12px 8px 20px; text-align: center;
  .receipt-icon { font-size: 44px; color: #67c23a; }
  .receipt-title { margin: 10px 0 14px; font-size: 16px; font-weight: 600; }
  .receipt-row { margin-bottom: 8px; font-size: 13px; color: #606266; }
  .el-button { margin-top: 12px; }
}
.att-list { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; }
.submit-btn { transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1); }
.submit-btn:active { transform: scale(0.95); }
.fd-footer { display: flex; align-items: center; width: 100%; }
.fd-spacer { flex: 1; }

.fb-records { max-height: 68vh; overflow-y: auto; }
.fb-record { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px; margin-bottom: 10px; background: #fff; }
.fb-rec-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fb-rec-id { color: #909399; font-size: 12px; }
.fb-rec-title { font-weight: 600; flex: 1; min-width: 120px; }
.fb-rec-desc { margin-top: 6px; font-size: 12px; color: #606266; white-space: pre-wrap; }
.fb-rec-reply { margin-top: 8px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 8px 10px; }
.fb-rec-meta { font-size: 12px; color: #909399; }
.fb-rec-text { margin: 4px 0 0; white-space: pre-wrap; font-family: inherit; font-size: 12px; color: #334155; }
</style>
