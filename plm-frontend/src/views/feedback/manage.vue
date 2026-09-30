<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:140px">
            <el-option v-for="(label, value) in STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable placeholder="全部" style="width:140px">
            <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提交时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始" end-placeholder="结束" style="width:260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
          <el-button icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="feedbackNo" label="反馈编号" width="170" fixed="left" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{row}"><el-tag size="small" effect="plain">{{ typeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="expectedResult" label="期望结果" min-width="180" show-overflow-tooltip />
        <el-table-column prop="pagePath" label="来源页面" min-width="180" show-overflow-tooltip />
        <el-table-column prop="submitterName" label="提交人" width="100" />
        <el-table-column prop="contact" label="联系方式" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" :disabled="isTerminal(row.status)"
              @click="openTransition(row)">流转</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total,sizes,prev,pager,next,jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" title="反馈流转" width="560px">
      <el-descriptions v-if="dialog.row" :column="1" border size="small" class="transition-desc">
        <el-descriptions-item label="反馈编号">{{ dialog.row.feedbackNo }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ statusLabel(dialog.row.status) }}</el-descriptions-item>
        <el-descriptions-item label="反馈内容">{{ dialog.row.content }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="transitionForm" label-width="100px">
        <el-form-item label="目标状态">
          <el-radio-group v-model="transitionForm.targetStatus">
            <el-radio v-for="s in nextStatuses" :key="s" :value="s">{{ statusLabel(s) }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="立项 issue">
          <el-input v-model="transitionForm.relatedIssueNo" placeholder="仅「已立项」需回填，如 PAND-999"
            :disabled="transitionForm.targetStatus !== 'INITIATED'" />
        </el-form-item>
        <el-form-item label="结论/原因">
          <el-input v-model="transitionForm.note" type="textarea" :rows="4"
            placeholder="评估结论；退回时须写明不满足的条款编号与原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="submitTransition">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pageFeedback, transitionFeedback } from '@/api/feedback'

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
// 与后端 FeedbackConstants.TRANSITIONS 一致(AC F13)
const TRANSITIONS = {
  PENDING: ['EVALUATING'],
  EVALUATING: ['INITIATED', 'RETURNED'],
  INITIATED: [],
  RETURNED: []
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({ pageNum: 1, pageSize: 20, status: '', type: '' })

const dialog = reactive({ visible: false, row: null, submitting: false })
const transitionForm = reactive({ targetStatus: '', relatedIssueNo: '', note: '' })

const nextStatuses = computed(() =>
  dialog.row ? (TRANSITIONS[dialog.row.status] || []) : [])

function statusLabel(status) {
  return STATUS_LABELS[status] || status
}
function statusTagType(status) {
  return STATUS_TAG_TYPES[status] || 'info'
}
function typeLabel(type) {
  return TYPES.find(t => t.value === type)?.label || type
}
function isTerminal(status) {
  return (TRANSITIONS[status] || []).length === 0
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageFeedback({
      ...query,
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || ''
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadData()
}

function reset() {
  query.status = ''
  query.type = ''
  dateRange.value = []
  search()
}

function openTransition(row) {
  dialog.row = row
  transitionForm.targetStatus = (TRANSITIONS[row.status] || [])[0] || ''
  transitionForm.relatedIssueNo = row.relatedIssueNo || ''
  transitionForm.note = ''
  dialog.visible = true
}

watch(() => transitionForm.targetStatus, (v) => {
  if (v !== 'INITIATED') transitionForm.relatedIssueNo = ''
})

async function submitTransition() {
  if (!transitionForm.targetStatus) {
    ElMessage.warning('请选择目标状态')
    return
  }
  if (!transitionForm.note.trim()) {
    ElMessage.warning('请填写分析结论或退回原因')
    return
  }
  if (transitionForm.targetStatus === 'INITIATED' && !transitionForm.relatedIssueNo.trim()) {
    ElMessage.warning('立项流转必须回填立项 issue 编号')
    return
  }
  dialog.submitting = true
  try {
    await transitionFeedback(dialog.row.id, {
      targetStatus: transitionForm.targetStatus,
      note: transitionForm.note.trim(),
      relatedIssueNo: transitionForm.relatedIssueNo.trim()
    })
    ElMessage.success('流转成功')
    dialog.visible = false
    loadData()
  } catch {
    // 失败提示由请求拦截器统一弹出
  } finally {
    dialog.submitting = false
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.transition-desc { margin-bottom: 16px; }
</style>
