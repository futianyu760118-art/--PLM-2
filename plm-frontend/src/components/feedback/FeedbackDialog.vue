<template>
  <el-dialog
    :model-value="modelValue"
    title="反馈"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="v => emit('update:modelValue', v)"
    @open="syncPagePath"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="提交反馈" name="submit">
        <!-- 回执:编号来自服务端响应,重开弹窗不重新生成(AC F10) -->
        <div v-if="receipt" class="receipt">
          <el-icon class="receipt-icon"><CircleCheckFilled /></el-icon>
          <div class="receipt-title">提交成功</div>
          <div class="receipt-row">反馈编号：<b>{{ receipt.feedbackNo }}</b></div>
          <div class="receipt-row">提交时间：{{ receipt.createdAt }}</div>
          <el-button type="primary" plain @click="startNew">再提一条</el-button>
        </div>

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="88px">
          <el-form-item label="反馈类型" prop="type">
            <el-radio-group v-model="form.type">
              <el-radio v-for="t in TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="问题描述" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="4"
              placeholder="请描述你遇到的问题或诉求（10-2000 字）" />
            <span class="counter">{{ form.content.length }}/2000</span>
          </el-form-item>
          <el-form-item label="期望结果" prop="expectedResult">
            <el-input v-model="form.expectedResult" type="textarea" :rows="3"
              placeholder="期望达成的结果（选择「需求」时必填）" />
            <span class="counter">{{ form.expectedResult.length }}/2000</span>
          </el-form-item>
          <el-form-item label="页面路径">
            <el-input :model-value="form.pagePath" readonly disabled />
          </el-form-item>
          <el-form-item label="联系方式" prop="contact">
            <el-input v-model="form.contact" placeholder="选填，便于回访" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="进度查询" name="query">
        <el-form :inline="true">
          <el-form-item label="反馈编号">
            <el-input v-model="queryNo" placeholder="如 FB-20260930-0001" clearable style="width:220px"
              @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="queryLoading" @click="handleQuery">查询</el-button>
          </el-form-item>
        </el-form>
        <el-descriptions v-if="queryResult" :column="2" border size="small">
          <el-descriptions-item label="反馈编号">{{ queryResult.feedbackNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(queryResult.status)" size="small">{{ statusLabel(queryResult.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(queryResult.type) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ queryResult.createdAt }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <template v-if="activeTab === 'submit' && !receipt" #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitFeedback, getFeedbackByNo } from '@/api/feedback'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])

const route = useRoute()
const formRef = ref(null)
const activeTab = ref('submit')
const submitting = ref(false)
const receipt = ref(null)

const TYPES = [
  { value: 'BUG', label: '缺陷' },
  { value: 'SUGGEST', label: '建议' },
  { value: 'REQUIREMENT', label: '需求' }
]
const STATUS_LABELS = {
  PENDING: '待分析', EVALUATING: '评估中', INITIATED: '已立项', RETURNED: '已退回'
}
const STATUS_TAG_TYPES = {
  PENDING: 'info', EVALUATING: 'warning', INITIATED: 'success', RETURNED: 'danger'
}

const form = reactive({ type: '', content: '', expectedResult: '', pagePath: '', contact: '' })

function syncPagePath() {
  form.pagePath = route.path
}

// 校验规则一次性覆盖全部字段,配合 scrollToField 满足 AC F8(一次列出全部错误并定位首个)
const rules = computed(() => ({
  type: [{ required: true, message: '反馈类型必选', trigger: 'change' }],
  content: [{ validator: validateContent, trigger: ['blur', 'change'] }],
  expectedResult: [{ validator: validateExpected, trigger: ['blur', 'change'] }],
  contact: [{ validator: validateContact, trigger: 'blur' }]
}))

function validateContent(rule, value, callback) {
  const v = (value || '').trim()
  if (!v) return callback(new Error('请填写问题描述'))
  if (v.length < 10 || v.length > 2000) return callback(new Error('问题描述长度需为 10-2000 字'))
  callback()
}

function validateExpected(rule, value, callback) {
  const v = (value || '').trim()
  if (form.type === 'REQUIREMENT' && !v) {
    return callback(new Error('反馈类型为「需求」时期望结果必填'))
  }
  if (v.length > 2000) return callback(new Error('期望结果长度不能超过 2000 字'))
  callback()
}

function validateContact(rule, value, callback) {
  if ((value || '').trim().length > 100) return callback(new Error('联系方式长度不能超过 100 字'))
  callback()
}

function firstErrors(fields) {
  return Object.keys(fields).map(p => fields[p][0]?.message).filter(Boolean)
}

async function handleSubmit() {
  let invalid = null
  try {
    await formRef.value.validate()
  } catch (fields) {
    invalid = fields
  }
  if (invalid) {
    const props = Object.keys(invalid)
    ElMessage.error(firstErrors(invalid).join('；'))
    formRef.value.scrollToField?.(props[0])
    return
  }
  submitting.value = true
  try {
    const res = await submitFeedback({ ...form })
    receipt.value = res.data
  } catch {
    // 失败提示(含 AC F11 重复提交)由请求拦截器统一弹出,此处不展示成功回执
  } finally {
    submitting.value = false
  }
}

async function startNew() {
  receipt.value = null
  Object.assign(form, { type: '', content: '', expectedResult: '', pagePath: route.path, contact: '' })
  await nextTick()
  formRef.value?.clearValidate()
}

const queryNo = ref('')
const queryLoading = ref(false)
const queryResult = ref(null)

async function handleQuery() {
  const no = queryNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入反馈编号')
    return
  }
  queryLoading.value = true
  try {
    queryResult.value = (await getFeedbackByNo(no)).data
  } catch {
    queryResult.value = null
  } finally {
    queryLoading.value = false
  }
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status
}
function statusTagType(status) {
  return STATUS_TAG_TYPES[status] || 'info'
}
function typeLabel(type) {
  return TYPES.find(t => t.value === type)?.label || type
}
</script>

<style lang="scss" scoped>
.receipt {
  padding: 12px 8px 20px;
  text-align: center;
  .receipt-icon { font-size: 44px; color: #67c23a; }
  .receipt-title { margin: 10px 0 14px; font-size: 16px; font-weight: 600; }
  .receipt-row { margin-bottom: 8px; font-size: 13px; color: #606266; }
  .el-button { margin-top: 12px; }
}
.counter {
  flex: 1 0 100%;
  text-align: right;
  font-size: 12px;
  line-height: 1.2;
  color: #909399;
}
</style>
