<template>
  <el-drawer
    :model-value="modelValue"
    title="问题反馈"
    direction="rtl"
    size="440px"
    :modal="false"
    :append-to-body="true"
    :close-on-click-modal="false"
    @update:model-value="v => emit('update:modelValue', v)"
    @open="onOpen"
  >
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

    <template v-if="!receipt" #footer>
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
import { createFeedback, uploadFeedbackFile } from '@/api/feedback'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'minimize'])

const route = useRoute()
const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const receipt = ref(null)

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

const form = reactive({ type: 'issue', severity: 'low', title: '', description: '', pagePath: '', attachments: [] })

const rules = computed(() => ({
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  severity: [{ required: true, message: '请选择严重度', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  description: [{ required: true, message: '请填写描述', trigger: 'blur' }]
}))

function onOpen() {
  receipt.value = null
  form.pagePath = route.path
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
</style>
